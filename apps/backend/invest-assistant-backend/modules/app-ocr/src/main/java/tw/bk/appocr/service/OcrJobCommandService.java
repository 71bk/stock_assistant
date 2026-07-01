package tw.bk.appocr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

/** User-initiated OCR job lifecycle commands. */
@Service
class OcrJobCommandService {
    private static final String JOB_QUEUED = OcrJobStatus.QUEUED.name();
    private static final String JOB_CANCELLED = OcrJobStatus.CANCELLED.name();
    private static final String STATUS_DRAFT = StatementStatus.DRAFT.name();
    private static final String STATUS_SUPERSEDED = StatementStatus.SUPERSEDED.name();

    private final OcrJobRepository ocrJobRepository;
    private final StatementRepository statementRepository;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrQueuePublisher queuePublisher;
    private final OcrPdfPasswordVault pdfPasswordVault;
    private final OcrViewMapper viewMapper;
    private final OcrJobStatePolicy statePolicy;
    private final ClockProvider clockProvider;

    OcrJobCommandService(
            OcrJobRepository ocrJobRepository,
            StatementRepository statementRepository,
            StatementTradeRepository statementTradeRepository,
            OcrQueuePublisher queuePublisher,
            OcrPdfPasswordVault pdfPasswordVault,
            OcrViewMapper viewMapper,
            OcrJobStatePolicy statePolicy,
            ClockProvider clockProvider) {
        this.ocrJobRepository = ocrJobRepository;
        this.statementRepository = statementRepository;
        this.statementTradeRepository = statementTradeRepository;
        this.queuePublisher = queuePublisher;
        this.pdfPasswordVault = pdfPasswordVault;
        this.viewMapper = viewMapper;
        this.statePolicy = statePolicy;
        this.clockProvider = clockProvider;
    }

    @Transactional
    public OcrJobView submitPdfPassword(Long userId, Long jobId, String pdfPassword) {
        requireUser(userId);
        if (pdfPassword == null || pdfPassword.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF password is required");
        }

        OcrJobEntity job = lockJob(userId, jobId);
        if (statePolicy.isDone(job.getStatus())) {
            return viewMapper.toJobView(job);
        }
        statePolicy.validatePasswordSubmission(job.getStatus(), job.getUpdatedAt());

        queue(job, 5);
        pdfPasswordVault.put(userId, jobId, pdfPassword);
        return viewMapper.toJobView(job);
    }

    @Transactional
    public OcrJobView retryJob(Long userId, Long jobId, boolean force) {
        OcrJobEntity job = lockJob(userId, jobId);
        statePolicy.validateRetry(job.getStatus(), job.getUpdatedAt(), force);

        if (job.getStatementId() != null) {
            StatementEntity statement = requireStatement(job.getStatementId(), userId);
            statePolicy.validateRetryStatement(statement.getStatus());
            resetStatement(statement);
        }

        queue(job, 0);
        return viewMapper.toJobView(job);
    }

    @Transactional
    public OcrJobView reparse(Long userId, Long jobId, boolean force) {
        OcrJobEntity job = lockJob(userId, jobId);
        statePolicy.validateReparse(job.getStatus(), job.getUpdatedAt(), force);
        if (job.getStatementId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "OCR job has no statement");
        }

        StatementEntity oldStatement = requireStatement(job.getStatementId(), userId);
        statePolicy.validateReparseStatement(oldStatement.getStatus(), force);
        oldStatement.setStatus(STATUS_SUPERSEDED);
        oldStatement.setSupersededAt(clockProvider.nowUtc());
        statementRepository.save(oldStatement);

        StatementEntity newStatement = copyAsDraft(oldStatement);
        statementRepository.save(newStatement);

        job.setStatementId(newStatement.getId());
        queue(job, 0);
        return viewMapper.toJobView(job);
    }

    @Transactional
    public OcrJobView cancel(Long userId, Long jobId, boolean force) {
        OcrJobEntity job = lockJob(userId, jobId);
        if (statePolicy.isTerminalForCancel(job.getStatus())) {
            return viewMapper.toJobView(job);
        }

        job.setStatus(JOB_CANCELLED);
        job.setProgress(100);
        job.setErrorMessage("Cancelled by user");
        return viewMapper.toJobView(ocrJobRepository.save(job));
    }

    @Transactional
    public OcrJobView requeueDuplicate(Long userId, Long jobId) {
        OcrJobEntity job = lockJob(userId, jobId);
        if (statePolicy.shouldRequeueDuplicate(job.getStatus())) {
            resetStatementIfReprocessable(job.getStatementId(), userId);
            queue(job, 0);
        }
        return viewMapper.toJobView(job);
    }

    private void queue(OcrJobEntity job, int progress) {
        job.setStatus(JOB_QUEUED);
        job.setProgress(progress);
        job.setErrorMessage(null);
        OcrJobEntity saved = ocrJobRepository.save(job);
        queuePublisher.enqueueAfterCommit(saved);
    }

    private void resetStatement(StatementEntity statement) {
        statementTradeRepository.deleteByStatementId(statement.getId());
        statement.setRawText(null);
        statement.setParsedJson("{}");
        statement.setStatus(STATUS_DRAFT);
        statementRepository.save(statement);
    }

    private void resetStatementIfReprocessable(Long statementId, Long userId) {
        if (statementId == null) {
            return;
        }
        StatementEntity statement = statementRepository.findByIdAndUserId(statementId, userId).orElse(null);
        if (statement == null || StatementStatus.CONFIRMED.name().equals(statement.getStatus())) {
            return;
        }
        resetStatement(statement);
    }

    private StatementEntity copyAsDraft(StatementEntity source) {
        StatementEntity statement = new StatementEntity();
        statement.setUserId(source.getUserId());
        statement.setPortfolioId(source.getPortfolioId());
        statement.setSource(source.getSource());
        statement.setFileId(source.getFileId());
        statement.setStatus(STATUS_DRAFT);
        statement.setParsedJson("{}");
        return statement;
    }

    private OcrJobEntity lockJob(Long userId, Long jobId) {
        return ocrJobRepository.findByIdAndUserIdForUpdate(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OCR job not found"));
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        return statementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Statement not found"));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
    }
}
