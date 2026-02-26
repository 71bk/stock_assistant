package tw.bk.appocr.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {
    private static final String SOURCE_OCR = TradeSource.OCR.name();
    private static final String STATUS_DRAFT = StatementStatus.DRAFT.name();
    private static final String STATUS_CONFIRMED = StatementStatus.CONFIRMED.name();
    private static final String STATUS_SUPERSEDED = StatementStatus.SUPERSEDED.name();
    private static final String JOB_QUEUED = OcrJobStatus.QUEUED.name();
    private static final String JOB_RUNNING = OcrJobStatus.RUNNING.name();
    private static final String JOB_DONE = OcrJobStatus.DONE.name();
    private static final String JOB_FAILED = OcrJobStatus.FAILED.name();
    private static final String JOB_CANCELLED = OcrJobStatus.CANCELLED.name();

    private final FileRepository fileRepository;
    private final StatementRepository statementRepository;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrJobRepository ocrJobRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;
    private final OcrQueueService queueService;
    private final OcrDedupeService dedupeService;
    private final StockTradeRepository stockTradeRepository;
    private final OcrJobProcessor jobProcessor;
    private final OcrDraftService ocrDraftService;
    private final OcrDedupeContentKeyResolver dedupeContentKeyResolver;
    private final OcrTradeCommandFactory tradeCommandFactory;
    private final OcrViewMapper viewMapper;

    @Value("${app.ocr.queue.max-running-minutes:30}")
    private long maxRunningMinutes;

    /**
     * 建立 OCR Job。
     *
     * @param userId      使用者 ID
     * @param fileId      檔案 ID
     * @param portfolioId 投資組合 ID
     * @param force       是否強制重新處理（忽略去重邏輯）
     * @return OCR Job 實體
     */
    @Transactional
    public OcrJobView createJob(Long userId, Long fileId, Long portfolioId, boolean force) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        FileEntity file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "File not found"));
        portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));

        String sha256 = file.getSha256();
        String dedupeContentKey = dedupeContentKeyResolver.resolve(file);
        log.info("建立 OCR Job: sha256={}, force={}", sha256, force);

        // 如果不是強制模式，檢查去重
        boolean reserved = false;
        if (!force && dedupeContentKey != null) {
            Optional<Long> existingJobId = dedupeService.findJobId(userId, dedupeContentKey, portfolioId);
            if (existingJobId.isPresent()) {
                log.info("發現既存 Job ID: {}", existingJobId.get());
                OcrJobEntity existing = ocrJobRepository.findByIdAndUserId(existingJobId.get(), userId).orElse(null);
                if (existing != null) {
                    log.info("既存 Job 狀態: {}", existing.getStatus());

                    // 如果 job 失敗或仍在 QUEUED 狀態，重新加入 queue
                    if (JOB_FAILED.equals(existing.getStatus()) || JOB_QUEUED.equals(existing.getStatus())) {
                        existing.setStatus(JOB_QUEUED);
                        existing.setProgress(0);
                        existing.setErrorMessage(null);
                        ocrJobRepository.save(existing);
                        queueService.enqueue(existing);
                    }
                    return viewMapper.toJobView(existing);
                }
            }

            reserved = dedupeService.reserve(userId, dedupeContentKey, portfolioId);
            if (!reserved) {
                Optional<Long> jobId = dedupeService.findJobId(userId, dedupeContentKey, portfolioId);
                if (jobId.isPresent()) {
                    OcrJobEntity existing = ocrJobRepository.findByIdAndUserId(jobId.get(), userId).orElse(null);
                    if (existing != null) {
                        return viewMapper.toJobView(existing);
                    }
                }
                throw new BusinessException(ErrorCode.CONFLICT, "OCR job is being created, please retry");
            }
        } else if (!force) {
            log.warn("Skip OCR dedupe because dedupeContentKey is missing: fileId={}, userId={}",
                    file.getId(), userId);
        } else {
            log.info("強制模式：跳過去重邏輯，建立新 Job");
        }

        StatementEntity statement = new StatementEntity();
        statement.setUserId(userId);
        statement.setPortfolioId(portfolioId);
        statement.setSource(SOURCE_OCR);
        statement.setFileId(file.getId());
        statement.setStatus(STATUS_DRAFT);
        statement.setParsedJson("{}");
        statementRepository.save(statement);

        OcrJobEntity job = new OcrJobEntity();
        job.setUserId(userId);
        job.setFileId(file.getId());
        job.setStatementId(statement.getId());
        job.setStatus(JOB_QUEUED);
        job.setProgress(0);
        ocrJobRepository.save(job);

        if (!force && dedupeContentKey != null) {
            dedupeService.store(userId, dedupeContentKey, portfolioId, job.getId());
        }
        queueService.enqueue(job);
        return viewMapper.toJobView(job);
    }

    /**
     * 處理 OCR Job。
     * 注意：不使用 @Transactional，讓每個步驟獨立執行，
     * 這樣即使處理失敗，也能正確更新 job 狀態。
     */
    public void processJob(Long userId, Long jobId) {
        jobProcessor.processJob(userId, jobId);
    }

    @Transactional(readOnly = true)
    public OcrJobView getJob(Long userId, Long jobId) {
        return viewMapper.toJobView(getJobEntity(userId, jobId));
    }

    private OcrJobEntity getJobEntity(Long userId, Long jobId) {
        return ocrJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OCR job not found"));
    }

    @Transactional(readOnly = true)
    public List<OcrDraftView> getDrafts(Long userId, Long jobId) {
        OcrJobEntity job = getJobEntity(userId, jobId);
        if (job.getStatementId() == null) {
            return List.of();
        }
        StatementEntity statement = requireStatement(job.getStatementId(), userId);
        if (statement == null) {
            return List.of();
        }
        return statementTradeRepository.findByStatementIdOrderByIdAsc(statement.getId())
                .stream()
                .map(viewMapper::toDraftView)
                .toList();
    }

    @Transactional
    public OcrJobView retryJob(Long userId, Long jobId, boolean force) {
        OcrJobEntity job = getJobEntity(userId, jobId);
        String status = job.getStatus();

        if (JOB_DONE.equals(status) && !force) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job already completed");
        }

        if (JOB_RUNNING.equals(status) && !force) {
            OffsetDateTime updatedAt = job.getUpdatedAt();
            if (updatedAt != null && updatedAt.isAfter(OffsetDateTime.now().minusMinutes(maxRunningMinutes))) {
                throw new BusinessException(ErrorCode.CONFLICT, "OCR job is still running");
            }
        }

        if (job.getStatementId() != null) {
            StatementEntity statement = requireStatement(job.getStatementId(), userId);
            if (STATUS_CONFIRMED.equals(statement.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "OCR statement already confirmed");
            }
            statementTradeRepository.deleteByStatementId(statement.getId());
            statement.setRawText(null);
            statement.setParsedJson("{}");
            statement.setStatus(STATUS_DRAFT);
            statementRepository.save(statement);
        }

        job.setStatus(JOB_QUEUED);
        job.setProgress(0);
        job.setErrorMessage(null);
        ocrJobRepository.save(job);
        queueService.enqueue(job);
        return viewMapper.toJobView(job);
    }

    @Transactional
    public OcrJobView reparse(Long userId, Long jobId, boolean force) {
        OcrJobEntity job = ocrJobRepository.findByIdAndUserIdForUpdate(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OCR job not found"));
        String status = job.getStatus();

        if (JOB_RUNNING.equals(status) && !force) {
            OffsetDateTime updatedAt = job.getUpdatedAt();
            if (updatedAt != null && updatedAt.isAfter(OffsetDateTime.now().minusMinutes(maxRunningMinutes))) {
                throw new BusinessException(ErrorCode.CONFLICT, "OCR job is still running");
            }
        }

        if (job.getStatementId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job has no statement");
        }

        StatementEntity oldStatement = requireStatement(job.getStatementId(), userId);
        if (STATUS_CONFIRMED.equals(oldStatement.getStatus()) && !force) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR statement already confirmed");
        }

        OffsetDateTime now = OffsetDateTime.now();
        oldStatement.setStatus(STATUS_SUPERSEDED);
        oldStatement.setSupersededAt(now);
        statementRepository.save(oldStatement);

        StatementEntity newStatement = new StatementEntity();
        newStatement.setUserId(oldStatement.getUserId());
        newStatement.setPortfolioId(oldStatement.getPortfolioId());
        newStatement.setSource(oldStatement.getSource());
        newStatement.setFileId(oldStatement.getFileId());
        newStatement.setStatus(STATUS_DRAFT);
        newStatement.setParsedJson("{}");
        statementRepository.save(newStatement);

        job.setStatementId(newStatement.getId());
        job.setStatus(JOB_QUEUED);
        job.setProgress(0);
        job.setErrorMessage(null);
        ocrJobRepository.save(job);
        queueService.enqueue(job);
        return viewMapper.toJobView(job);
    }

    @Transactional
    public OcrJobView cancel(Long userId, Long jobId, boolean force) {
        OcrJobEntity job = getJobEntity(userId, jobId);
        String status = job.getStatus();
        if (!force && JOB_RUNNING.equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job is still running");
        }
        if (JOB_DONE.equals(status) || JOB_FAILED.equals(status) || JOB_CANCELLED.equals(status)) {
            return viewMapper.toJobView(job);
        }

        job.setStatus(JOB_CANCELLED);
        job.setProgress(100);
        job.setErrorMessage("Cancelled by user");
        return viewMapper.toJobView(ocrJobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public Long getPortfolioIdByJob(Long userId, Long jobId) {
        OcrJobEntity job = getJobEntity(userId, jobId);
        if (job.getStatementId() == null) {
            return null;
        }
        StatementEntity statement = requireStatement(job.getStatementId(), userId);
        return statement.getPortfolioId();
    }

    @Transactional(readOnly = true)
    public Long getPortfolioIdByStatementId(Long userId, Long statementId) {
        if (statementId == null) {
            return null;
        }
        StatementEntity statement = requireStatement(statementId, userId);
        return statement.getPortfolioId();
    }

    @Transactional
    public OcrDraftView updateDraft(Long userId, Long draftId, OcrDraftUpdate update) {
        return viewMapper.toDraftView(ocrDraftService.updateDraft(userId, draftId, update));
    }

    /**
     * Confirm and import selected drafts.
     *
     * @param userId           User ID
     * @param jobId            OCR Job ID
     * @param selectedDraftIds Draft IDs to import. If null or empty, imports all
     *                         drafts.
     * @return ConfirmResult with importedCount and errors list
     */
    @Transactional
    public ConfirmResult confirm(Long userId, Long jobId, Set<Long> selectedDraftIds) {
        OcrJobEntity job = getJobEntity(userId, jobId);
        if (!JOB_DONE.equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job not completed");
        }
        if (job.getStatementId() == null) {
            return ConfirmResult.builder().importedCount(0).errors(List.of()).build();
        }
        StatementEntity statement = requireStatement(job.getStatementId(), userId);
        List<StatementTradeEntity> allDrafts = statementTradeRepository
                .findByStatementIdOrderByIdAsc(statement.getId());
        if (allDrafts.isEmpty()) {
            return ConfirmResult.builder().importedCount(0).errors(List.of()).build();
        }

        // Filter drafts to import
        List<StatementTradeEntity> draftsToImport;
        if (selectedDraftIds == null || selectedDraftIds.isEmpty()) {
            // Import all drafts (backward compatible)
            draftsToImport = allDrafts;
        } else {
            // Import only selected drafts
            draftsToImport = allDrafts.stream()
                    .filter(d -> selectedDraftIds.contains(d.getId()))
                    .toList();
        }

        if (draftsToImport.isEmpty()) {
            return ConfirmResult.builder().importedCount(0).errors(List.of()).build();
        }

        // Validate and import drafts
        int importedCount = 0;
        List<Long> importedIds = new ArrayList<>();
        List<ConfirmResult.DraftError> errors = new ArrayList<>();

        for (StatementTradeEntity draft : draftsToImport) {
            // Validate: check instrumentId
            if (draft.getInstrumentId() == null) {
                errors.add(ConfirmResult.DraftError.builder()
                        .draftId(draft.getId())
                        .reason("缺少股票代碼 (instrumentId)")
                        .build());
                continue;
            }

            // Validate: check duplicate
            boolean isDuplicate = ocrDraftService.isDuplicateDraft(draft, statement.getPortfolioId());
            if (isDuplicate) {
                errors.add(ConfirmResult.DraftError.builder()
                        .draftId(draft.getId())
                        .reason("重複交易（相同股票、日期、買賣、數量、價格）")
                        .build());
                continue;
            }

            // Import the draft
            try {
                TradeCommand command = tradeCommandFactory.toTradeCommand(draft);
                if (command == null) {
                    errors.add(ConfirmResult.DraftError.builder()
                            .draftId(draft.getId())
                            .reason("買賣方向無效 (side)")
                            .build());
                    continue;
                }
                portfolioService.createTrade(userId, statement.getPortfolioId(), command);
                importedIds.add(draft.getId());
                importedCount++;
            } catch (BusinessException ex) {
                log.warn("Failed to import draft: draftId={}, error={}", draft.getId(), ex.getMessage());
                errors.add(ConfirmResult.DraftError.builder()
                        .draftId(draft.getId())
                        .reason(ex.getMessage())
                        .build());
            }
        }

        // Delete only imported drafts
        for (Long draftId : importedIds) {
            statementTradeRepository.deleteById(draftId);
        }
        log.info("已匯入並刪除草稿: statementId={}, importedCount={}, errorCount={}",
                statement.getId(), importedCount, errors.size());

        // Check if all drafts are processed
        List<StatementTradeEntity> remainingDrafts = statementTradeRepository
                .findByStatementIdOrderByIdAsc(statement.getId());
        if (remainingDrafts.isEmpty()) {
            // All drafts processed, mark as confirmed
            statement.setStatus(STATUS_CONFIRMED);
            statementRepository.save(statement);
            int updated = ocrJobRepository.updateStatusIfNotCancelled(
                    jobId, JOB_DONE, 100, null, JOB_CANCELLED);
            if (updated == 0) {
                log.info("OCR Job cancelled before confirm, skip DONE update: jobId={}", jobId);
            } else {
                log.info("OCR Job 已完成: jobId={}", jobId);
            }
        }

        return ConfirmResult.builder()
                .importedCount(importedCount)
                .errors(errors)
                .build();
    }

    /**
     * Delete a single draft.
     *
     * @param userId  User ID
     * @param draftId Draft ID to delete
     */
    @Transactional
    public void deleteDraft(Long userId, Long draftId) {
        StatementTradeEntity draft = statementTradeRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Draft not found"));

        // Verify ownership
        StatementEntity statement = requireStatement(draft.getStatementId(), userId);
        OcrJobEntity job = ocrJobRepository.findByStatementId(statement.getId()).orElse(null);
        if (job != null && !JOB_DONE.equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job not completed");
        }

        statementTradeRepository.deleteById(draftId);
        log.info("已刪除草稿: draftId={}, statementId={}", draftId, statement.getId());

        // Check if all drafts are deleted
        List<StatementTradeEntity> remainingDrafts = statementTradeRepository
                .findByStatementIdOrderByIdAsc(statement.getId());
        if (remainingDrafts.isEmpty()) {
            // All drafts deleted (either imported or manually deleted), mark as confirmed
            statement.setStatus(STATUS_CONFIRMED);
            statementRepository.save(statement);

            // Find and update the job
            if (job != null) {
                int updated = ocrJobRepository.updateStatusIfNotCancelled(
                        job.getId(), JOB_DONE, 100, null, JOB_CANCELLED);
                if (updated == 0) {
                    log.info("OCR Job cancelled before deleteDraft completion, skip DONE update: jobId={}",
                            job.getId());
                } else {
                    log.info("OCR Job 已完成: jobId={}", job.getId());
                }
            }
        }
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        Optional<StatementEntity> statement = statementRepository.findByIdAndUserId(statementId, userId);
        return statement.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Statement not found"));
    }

}
