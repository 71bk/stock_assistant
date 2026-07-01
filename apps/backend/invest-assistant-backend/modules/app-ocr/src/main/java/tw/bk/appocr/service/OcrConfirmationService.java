package tw.bk.appocr.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

/**
 * OCR 草稿的確認匯入與刪除。
 *
 * <p>把 draft 逐筆驗證、去重、以獨立交易匯入，並在草稿清空後將 statement/job 標記完成。
 * 從 {@code OcrService} 抽出；交易語意（{@code @Transactional}）維持在 {@code OcrService}
 * 的委派方法上，逐筆匯入仍透過 {@code OcrImportTxService}（REQUIRES_NEW）隔離單筆失敗。
 */
@Slf4j
@Service
class OcrConfirmationService {
    private static final String STATUS_CONFIRMED = StatementStatus.CONFIRMED.name();
    private static final String JOB_DONE = OcrJobStatus.DONE.name();
    private static final String JOB_CANCELLED = OcrJobStatus.CANCELLED.name();

    private final StatementRepository statementRepository;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrJobRepository ocrJobRepository;
    private final OcrDraftService ocrDraftService;
    private final OcrImportTxService importTxService;

    OcrConfirmationService(StatementRepository statementRepository,
            StatementTradeRepository statementTradeRepository,
            OcrJobRepository ocrJobRepository,
            OcrDraftService ocrDraftService,
            OcrImportTxService importTxService) {
        this.statementRepository = statementRepository;
        this.statementTradeRepository = statementTradeRepository;
        this.ocrJobRepository = ocrJobRepository;
        this.ocrDraftService = ocrDraftService;
        this.importTxService = importTxService;
    }

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

            // Import each draft in its own (REQUIRES_NEW) transaction so a single
            // failure (currency mismatch, missing instrument, dedupe constraint, ...)
            // only rolls back that draft instead of poisoning the whole batch.
            try {
                importTxService.importDraft(userId, statement.getPortfolioId(), draft);
                importedCount++;
            } catch (BusinessException ex) {
                log.warn("Failed to import draft: draftId={}, error={}", draft.getId(), ex.getMessage());
                errors.add(ConfirmResult.DraftError.builder()
                        .draftId(draft.getId())
                        .reason(ex.getMessage())
                        .build());
            }
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

    private OcrJobEntity getJobEntity(Long userId, Long jobId) {
        return ocrJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OCR job not found"));
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        return statementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Statement not found"));
    }
}
