package tw.bk.appocr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

@Service
@RequiredArgsConstructor
public class OcrService {
    private static final String SOURCE_OCR = "OCR";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String JOB_DONE = "DONE";

    private final FileRepository fileRepository;
    private final StatementRepository statementRepository;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrJobRepository ocrJobRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional
    public OcrJobEntity createJob(Long userId, Long fileId, Long portfolioId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        FileEntity file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "檔案不存在"));
        portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "投資組合不存在"));

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
        job.setStatus(JOB_DONE);
        job.setProgress(100);
        return ocrJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public OcrJobEntity getJob(Long userId, Long jobId) {
        return ocrJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OCR 任務不存在"));
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "草稿不存在"));
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
            draft.setQuantity(update.quantity().setScale(6, RoundingMode.HALF_UP));
        }
        if (update.price() != null) {
            draft.setPrice(update.price().setScale(8, RoundingMode.HALF_UP));
        }
        if (update.currency() != null) {
            draft.setCurrency(update.currency().trim().toUpperCase());
        }
        if (update.fee() != null) {
            draft.setFee(update.fee().setScale(6, RoundingMode.HALF_UP));
        }
        if (update.tax() != null) {
            draft.setTax(update.tax().setScale(6, RoundingMode.HALF_UP));
        }

        draft.setRowHash(buildRowHash(statement.getId(), draft));
        statement.setStatus(STATUS_DRAFT);
        return statementTradeRepository.save(draft);
    }

    @Transactional
    public int confirm(Long userId, Long jobId) {
        OcrJobEntity job = getJob(userId, jobId);
        if (job.getStatementId() == null) {
            return 0;
        }
        StatementEntity statement = requireStatement(job.getStatementId(), userId);
        statement.setStatus(STATUS_CONFIRMED);
        statementRepository.save(statement);
        job.setProgress(100);
        job.setStatus(JOB_DONE);
        job.setErrorMessage(null);
        ocrJobRepository.save(job);
        return 0;
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        Optional<StatementEntity> statement = statementRepository.findByIdAndUserId(statementId, userId);
        return statement.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "匯入批次不存在"));
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
