package tw.bk.appocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.client.AiWorkerOcrClient;
import tw.bk.appocr.client.AiWorkerOcrResponse;
import tw.bk.appocr.client.AiWorkerParsedTrade;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.model.TradeSide;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.appfiles.config.FileStorageProperties;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {
    private static final String SOURCE_OCR = "OCR";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String JOB_QUEUED = "QUEUED";
    private static final String JOB_RUNNING = "RUNNING";
    private static final String JOB_DONE = "DONE";
    private static final String JOB_FAILED = "FAILED";
    private static final int AMOUNT_SCALE = 6;
    private static final int PRICE_SCALE = 8;

    private final FileRepository fileRepository;
    private final StatementRepository statementRepository;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrJobRepository ocrJobRepository;
    private final PortfolioRepository portfolioRepository;
    private final InstrumentRepository instrumentRepository;
    private final PortfolioService portfolioService;
    private final AiWorkerOcrClient aiWorkerOcrClient;
    private final ObjectMapper objectMapper;
    private final OcrQueueService queueService;
    private final OcrDedupeService dedupeService;
    private final FileStorageProperties fileStorageProperties;
    private final StockTradeRepository stockTradeRepository;

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
    public OcrJobEntity createJob(Long userId, Long fileId, Long portfolioId, boolean force) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        FileEntity file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "File not found"));
        portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));

        String sha256 = file.getSha256();
        log.info("檢查重複 Job: sha256={}, force={}", sha256, force);

        // 如果不是強制模式，檢查去重
        if (!force) {
            Optional<Long> existingJobId = dedupeService.findJobId(userId, sha256);
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
                    return existing;
                }
            }
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

        dedupeService.store(userId, sha256, job.getId());
        queueService.enqueue(job);
        return job;
    }

    /**
     * 處理 OCR Job。
     * 注意：不使用 @Transactional，讓每個步驟獨立執行，
     * 這樣即使處理失敗，也能正確更新 job 狀態。
     */
    public void processJob(Long userId, Long jobId) {
        if (userId == null || jobId == null) {
            return;
        }
        OcrJobEntity job = ocrJobRepository.findByIdAndUserId(jobId, userId).orElse(null);
        if (job == null) {
            log.warn("OCR Job 不存在: jobId={}, userId={}", jobId, userId);
            return;
        }
        if (JOB_DONE.equals(job.getStatus())) {
            log.info("OCR Job 已完成，跳過: jobId={}", jobId);
            return;
        }
        if (JOB_RUNNING.equals(job.getStatus())) {
            OffsetDateTime updatedAt = job.getUpdatedAt();
            if (updatedAt != null && updatedAt.isAfter(OffsetDateTime.now().minusMinutes(maxRunningMinutes))) {
                log.info("OCR Job 正在處理中，跳過: jobId={}", jobId);
                return;
            }
        }

        // 標記為 RUNNING
        try {
            job.setStatus(JOB_RUNNING);
            job.setProgress(5);
            ocrJobRepository.save(job);
        } catch (Exception ex) {
            log.error("無法更新 Job 狀態為 RUNNING: jobId={}, error={}", jobId, ex.getMessage());
            return;
        }

        try {
            StatementEntity statement = requireStatement(job.getStatementId(), userId);
            FileEntity file = fileRepository.findByIdAndUserId(job.getFileId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "File not found"));

            log.info("開始處理 OCR: jobId={}, fileId={}", jobId, file.getId());
            byte[] content = loadFileBytes(file);

            log.info("呼叫 AI Worker 進行 OCR...");
            AiWorkerOcrResponse response = aiWorkerOcrClient.processFile(userId, file, content);
            if (response == null) {
                throw new BusinessException(ErrorCode.OCR_PARSE_FAILED, "Empty OCR response");
            }

            log.info("OCR 完成，儲存結果...");
            statement.setRawText(response.rawText());
            statement.setParsedJson(objectMapper.writeValueAsString(response));
            statementRepository.save(statement);

            saveDrafts(statement, response.trades());

            job.setStatus(JOB_DONE);
            job.setProgress(100);
            job.setErrorMessage(null);
            ocrJobRepository.save(job);
            log.info("OCR Job 處理完成: jobId={}", jobId);

        } catch (Exception ex) {
            log.error("OCR Job 處理失敗: jobId={}, error={}", jobId, ex.getMessage(), ex);
            try {
                job.setStatus(JOB_FAILED);
                job.setProgress(100);
                job.setErrorMessage(trimMessage(ex.getMessage()));
                ocrJobRepository.save(job);
            } catch (Exception saveEx) {
                log.error("無法儲存 Job 失敗狀態: jobId={}, error={}", jobId, saveEx.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public OcrJobEntity getJob(Long userId, Long jobId) {
        return ocrJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OCR job not found"));
    }

    @Transactional(readOnly = true)
    public List<StatementTradeEntity> getDrafts(Long userId, Long jobId) {
        OcrJobEntity job = getJob(userId, jobId);
        if (job.getStatementId() == null) {
            return List.of();
        }
        StatementEntity statement = requireStatement(job.getStatementId(), userId);
        if (statement == null) {
            return List.of();
        }
        return statementTradeRepository.findByStatementIdOrderByIdAsc(statement.getId());
    }

    @Transactional
    public StatementTradeEntity updateDraft(Long userId, Long draftId, OcrDraftUpdate update) {
        StatementTradeEntity draft = statementTradeRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Draft not found"));
        StatementEntity statement = requireStatement(draft.getStatementId(), userId);

        if (update.instrumentId() != null) {
            draft.setInstrumentId(update.instrumentId());
        }
        if (update.rawTicker() != null) {
            draft.setRawTicker(update.rawTicker());
        }
        if (update.name() != null) {
            draft.setName(update.name());
        }
        if (update.tradeDate() != null) {
            draft.setTradeDate(update.tradeDate());
        }
        if (update.settlementDate() != null) {
            draft.setSettlementDate(update.settlementDate());
        }
        if (update.side() != null) {
            draft.setSide(update.side().name());
        }
        if (update.quantity() != null) {
            draft.setQuantity(update.quantity().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }
        if (update.price() != null) {
            draft.setPrice(update.price().setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        }
        if (update.currency() != null) {
            draft.setCurrency(update.currency().trim().toUpperCase(Locale.ROOT));
        }
        if (update.fee() != null) {
            draft.setFee(update.fee().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }
        if (update.tax() != null) {
            draft.setTax(update.tax().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }

        draft.setRowHash(buildRowHash(statement.getId(), draft));
        statement.setStatus(STATUS_DRAFT);
        return statementTradeRepository.save(draft);
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
        OcrJobEntity job = getJob(userId, jobId);
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
            boolean isDuplicate = stockTradeRepository
                    .existsByPortfolioIdAndInstrumentIdAndTradeDateAndSideAndQuantityAndPrice(
                            statement.getPortfolioId(),
                            draft.getInstrumentId(),
                            draft.getTradeDate(),
                            draft.getSide(),
                            draft.getQuantity(),
                            draft.getPrice());
            if (isDuplicate) {
                errors.add(ConfirmResult.DraftError.builder()
                        .draftId(draft.getId())
                        .reason("重複交易（相同股票、日期、買賣、數量、價格）")
                        .build());
                continue;
            }

            // Import the draft
            try {
                TradeCommand command = toTradeCommand(draft);
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
            job.setProgress(100);
            job.setStatus(JOB_DONE);
            job.setErrorMessage(null);
            ocrJobRepository.save(job);
            log.info("所有草稿已處理完畢，Job 狀態更新為 DONE: jobId={}", jobId);
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
            OcrJobEntity job = ocrJobRepository.findByStatementId(statement.getId()).orElse(null);
            if (job != null) {
                job.setProgress(100);
                job.setStatus(JOB_DONE);
                job.setErrorMessage(null);
                ocrJobRepository.save(job);
                log.info("所有草稿已處理完畢，Job 狀態更新為 DONE: jobId={}", job.getId());
            }
        }
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        Optional<StatementEntity> statement = statementRepository.findByIdAndUserId(statementId, userId);
        return statement.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Statement not found"));
    }

    private void saveDrafts(StatementEntity statement, List<AiWorkerParsedTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            return;
        }
        Set<String> seenHashes = new HashSet<>();
        List<StatementTradeEntity> entities = new ArrayList<>();
        for (AiWorkerParsedTrade trade : trades) {
            StatementTradeEntity entity = toDraftEntity(statement, trade);
            String rowHash = entity.getRowHash();
            if (rowHash == null || seenHashes.contains(rowHash)) {
                continue;
            }
            seenHashes.add(rowHash);
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            statementTradeRepository.saveAll(entities);
        }
    }

    private StatementTradeEntity toDraftEntity(StatementEntity statement, AiWorkerParsedTrade trade) {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setStatementId(statement.getId());
        entity.setRawTicker(trim(trade.ticker()));
        entity.setName(trim(trade.stockName()));
        entity.setTradeDate(trade.tradeDate());
        entity.setSettlementDate(trade.settlementDate());
        entity.setSide(normalizeSide(trade.side()));
        entity.setQuantity(normalizeQuantity(trade.quantity()));
        entity.setPrice(normalizePrice(trade.price()));

        InstrumentEntity instrument = resolveInstrument(trade.ticker());
        if (instrument != null) {
            entity.setInstrumentId(instrument.getId());
            if (entity.getName() == null) {
                entity.setName(firstNonBlank(instrument.getNameZh(), instrument.getNameEn()));
            }
        }

        String currency = normalizeCurrency(trade.currency(), instrument);
        entity.setCurrency(currency);
        entity.setFee(normalizeAmount(trade.fee()));
        entity.setTax(normalizeAmount(trade.tax()));
        entity.setNetAmount(calculateNetAmount(entity.getSide(), entity.getPrice(), entity.getQuantity(),
                entity.getFee(), entity.getTax()));

        entity.setWarningsJson(toJson(trade.warnings()));
        entity.setErrorsJson("[]");

        // 檢查是否已存在相同的交易（重複檢測）
        if (instrument != null && entity.getTradeDate() != null && entity.getSide() != null
                && entity.getQuantity() != null && entity.getPrice() != null) {
            boolean exists = stockTradeRepository
                    .existsByPortfolioIdAndInstrumentIdAndTradeDateAndSideAndQuantityAndPrice(
                            statement.getPortfolioId(),
                            instrument.getId(),
                            entity.getTradeDate(),
                            entity.getSide(),
                            entity.getQuantity(),
                            entity.getPrice());
            if (exists) {
                List<String> warnings = new ArrayList<>(trade.warnings() != null ? trade.warnings() : List.of());
                warnings.add("⚠️ 此交易可能已存在（相同股票、日期、買賣、數量、價格）");
                entity.setWarningsJson(toJson(warnings));
            }
        }

        entity.setRowHash(buildRowHash(statement.getId(), entity));
        return entity;
    }

    private TradeCommand toTradeCommand(StatementTradeEntity draft) {
        if (draft.getInstrumentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing instrumentId");
        }
        if (draft.getTradeDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing tradeDate");
        }
        if (draft.getSide() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing side");
        }
        if (draft.getQuantity() == null || draft.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft quantity must be > 0");
        }
        if (draft.getPrice() == null || draft.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft price must be > 0");
        }
        String currency = draft.getCurrency();
        if (currency == null || currency.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Draft missing currency");
        }

        TradeSide side = TradeSide.valueOf(draft.getSide().toUpperCase(Locale.ROOT));
        return new TradeCommand(
                draft.getInstrumentId(),
                draft.getTradeDate(),
                draft.getSettlementDate(),
                side,
                draft.getQuantity(),
                draft.getPrice(),
                currency,
                normalizeAmount(draft.getFee()),
                normalizeAmount(draft.getTax()),
                null,
                SOURCE_OCR);
    }

    private InstrumentEntity resolveInstrument(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        return instrumentRepository.findFirstByTickerIgnoreCase(ticker.trim()).orElse(null);
    }

    private String normalizeSide(String side) {
        if (side == null) {
            return null;
        }
        return side.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return null;
        }
        return quantity.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount cannot be negative");
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency, InstrumentEntity instrument) {
        if (currency != null && !currency.isBlank()) {
            return currency.trim().toUpperCase(Locale.ROOT);
        }
        if (instrument != null && instrument.getCurrency() != null) {
            return instrument.getCurrency();
        }
        return null;
    }

    private BigDecimal calculateNetAmount(String side, BigDecimal price, BigDecimal quantity,
            BigDecimal fee, BigDecimal tax) {
        if (price == null || quantity == null || side == null) {
            return null;
        }
        BigDecimal gross = price.multiply(quantity).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal fees = normalizeAmount(fee).add(normalizeAmount(tax))
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if ("BUY".equalsIgnoreCase(side)) {
            return gross.add(fees).negate().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return gross.subtract(fees).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private String toJson(List<String> warnings) {
        try {
            if (warnings == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(warnings);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private byte[] loadFileBytes(FileEntity file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        if (file.getProvider() != null && !"local".equalsIgnoreCase(file.getProvider())) {
            throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
        }
        String localPath = fileStorageProperties.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            localPath = "./data/uploads";
        }
        Path baseDir = Paths.get(localPath).toAbsolutePath().normalize();
        Path path = baseDir.resolve(file.getObjectKey()).normalize();
        log.info("OCR 檔案路徑: baseDir={}, objectKey={}, fullPath={}", baseDir, file.getObjectKey(), path);

        if (!Files.exists(path)) {
            log.error("檔案不存在: {}", path);
            throw new BusinessException(ErrorCode.NOT_FOUND, "檔案不存在: " + path);
        }

        try {
            return Files.readAllBytes(path);
        } catch (Exception ex) {
            log.error("讀取檔案失敗: path={}, error={}", path, ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file: " + path);
        }
    }

    private String buildRowHash(Long statementId, StatementTradeEntity trade) {
        String payload = statementId + "|"
                + safe(trade.getInstrumentId()) + "|"
                + safe(trade.getRawTicker()) + "|"
                + safe(trade.getTradeDate()) + "|"
                + safe(trade.getSide()) + "|"
                + safe(trade.getQuantity()) + "|"
                + safe(trade.getPrice()) + "|"
                + safe(trade.getCurrency()) + "|"
                + safe(trade.getFee()) + "|"
                + safe(trade.getTax());
        return sha256Hex(payload);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256Hex(String input) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 not available");
        }
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
