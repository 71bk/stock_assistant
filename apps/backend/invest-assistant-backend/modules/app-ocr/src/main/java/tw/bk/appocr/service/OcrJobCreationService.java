package tw.bk.appocr.service;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.enums.TradeSource;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;

/** Creates OCR jobs and coordinates duplicate reservations. */
@Slf4j
@Service
class OcrJobCreationService {
    private static final String SOURCE_OCR = TradeSource.OCR.name();
    private static final String STATUS_DRAFT = StatementStatus.DRAFT.name();
    private static final String JOB_QUEUED = OcrJobStatus.QUEUED.name();

    private final FileRepository fileRepository;
    private final StatementRepository statementRepository;
    private final OcrJobRepository ocrJobRepository;
    private final PortfolioRepository portfolioRepository;
    private final OcrDedupeService dedupeService;
    private final OcrDedupeContentKeyResolver dedupeContentKeyResolver;
    private final OcrJobCommandService commandService;
    private final OcrQueuePublisher queuePublisher;
    private final OcrViewMapper viewMapper;

    OcrJobCreationService(
            FileRepository fileRepository,
            StatementRepository statementRepository,
            OcrJobRepository ocrJobRepository,
            PortfolioRepository portfolioRepository,
            OcrDedupeService dedupeService,
            OcrDedupeContentKeyResolver dedupeContentKeyResolver,
            OcrJobCommandService commandService,
            OcrQueuePublisher queuePublisher,
            OcrViewMapper viewMapper) {
        this.fileRepository = fileRepository;
        this.statementRepository = statementRepository;
        this.ocrJobRepository = ocrJobRepository;
        this.portfolioRepository = portfolioRepository;
        this.dedupeService = dedupeService;
        this.dedupeContentKeyResolver = dedupeContentKeyResolver;
        this.commandService = commandService;
        this.queuePublisher = queuePublisher;
        this.viewMapper = viewMapper;
    }

    @Transactional
    public OcrJobView createJob(Long userId, Long fileId, Long portfolioId, boolean force) {
        requireUser(userId);
        FileEntity file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "File not found"));
        portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));

        String dedupeContentKey = dedupeContentKeyResolver.resolve(file);
        log.info("Create OCR job: fileId={}, force={}", file.getId(), force);

        if (!force && dedupeContentKey != null) {
            Optional<Long> existingJobId = dedupeService.findJobId(userId, dedupeContentKey, portfolioId);
            if (existingJobId.isPresent()) {
                OcrJobEntity existing = ocrJobRepository.findByIdAndUserId(existingJobId.get(), userId).orElse(null);
                if (existing != null) {
                    return commandService.requeueDuplicate(userId, existing.getId());
                }
            }

            if (!dedupeService.reserve(userId, dedupeContentKey, portfolioId)) {
                Optional<Long> visibleJobId = dedupeService.findJobId(userId, dedupeContentKey, portfolioId);
                if (visibleJobId.isPresent()) {
                    OcrJobEntity existing = ocrJobRepository.findByIdAndUserId(visibleJobId.get(), userId).orElse(null);
                    if (existing != null) {
                        return viewMapper.toJobView(existing);
                    }
                }
                throw new BusinessException(ErrorCode.CONFLICT, "OCR job is being created, please retry");
            }
        } else if (!force) {
            log.warn("Skip OCR dedupe because dedupeContentKey is missing: fileId={}, userId={}", file.getId(), userId);
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
        queuePublisher.enqueueAfterCommit(job);
        return viewMapper.toJobView(job);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
    }
}
