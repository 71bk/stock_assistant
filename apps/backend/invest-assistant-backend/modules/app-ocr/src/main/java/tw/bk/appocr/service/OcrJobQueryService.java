package tw.bk.appocr.service;

import java.util.List;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

/**
 * OCR job / draft 的唯讀查詢。
 *
 * <p>從 {@code OcrService} 抽出查詢職責；交易語意（{@code @Transactional(readOnly)}）
 * 維持在 {@code OcrService} 的委派方法上。
 */
class OcrJobQueryService {

    private final OcrJobRepository ocrJobRepository;
    private final StatementRepository statementRepository;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrViewMapper viewMapper;

    OcrJobQueryService(OcrJobRepository ocrJobRepository,
            StatementRepository statementRepository,
            StatementTradeRepository statementTradeRepository,
            OcrViewMapper viewMapper) {
        this.ocrJobRepository = ocrJobRepository;
        this.statementRepository = statementRepository;
        this.statementTradeRepository = statementTradeRepository;
        this.viewMapper = viewMapper;
    }

    OcrJobView getJob(Long userId, Long jobId) {
        return viewMapper.toJobView(getJobEntity(userId, jobId));
    }

    List<OcrDraftView> getDrafts(Long userId, Long jobId) {
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

    Long getPortfolioIdByJob(Long userId, Long jobId) {
        OcrJobEntity job = getJobEntity(userId, jobId);
        if (job.getStatementId() == null) {
            return null;
        }
        StatementEntity statement = requireStatement(job.getStatementId(), userId);
        return statement.getPortfolioId();
    }

    Long getPortfolioIdByStatementId(Long userId, Long statementId) {
        if (statementId == null) {
            return null;
        }
        StatementEntity statement = requireStatement(statementId, userId);
        return statement.getPortfolioId();
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
