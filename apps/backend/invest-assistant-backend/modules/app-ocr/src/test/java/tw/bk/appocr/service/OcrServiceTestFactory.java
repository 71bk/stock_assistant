package tw.bk.appocr.service;

import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appcommon.time.SystemClockProvider;
import tw.bk.appocr.queue.OcrDedupeService;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.apppersistence.repository.FileRepository;
import tw.bk.apppersistence.repository.OcrJobRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

final class OcrServiceTestFactory {
    private OcrServiceTestFactory() {
    }

    static OcrService create(
            FileRepository fileRepository,
            StatementRepository statementRepository,
            StatementTradeRepository statementTradeRepository,
            OcrJobRepository ocrJobRepository,
            PortfolioRepository portfolioRepository,
            OcrQueueService queueService,
            OcrDedupeService dedupeService,
            OcrPdfPasswordVault pdfPasswordVault,
            OcrJobProcessor jobProcessor,
            OcrDraftService draftService,
            OcrDedupeContentKeyResolver dedupeContentKeyResolver,
            OcrImportTxService importTxService,
            OcrViewMapper viewMapper) {
        ClockProvider clockProvider = new SystemClockProvider();
        OcrQueuePublisher queuePublisher = new OcrQueuePublisher(queueService);
        OcrJobStatePolicy statePolicy = new OcrJobStatePolicy(clockProvider, 30L);
        OcrJobCommandService commandService = new OcrJobCommandService(
                ocrJobRepository,
                statementRepository,
                statementTradeRepository,
                queuePublisher,
                pdfPasswordVault,
                viewMapper,
                statePolicy,
                clockProvider);
        OcrJobCreationService creationService = new OcrJobCreationService(
                fileRepository,
                statementRepository,
                ocrJobRepository,
                portfolioRepository,
                dedupeService,
                dedupeContentKeyResolver,
                commandService,
                queuePublisher,
                viewMapper);
        OcrJobQueryService queryService = new OcrJobQueryService(
                ocrJobRepository,
                statementRepository,
                statementTradeRepository,
                viewMapper);
        OcrConfirmationService confirmationService = new OcrConfirmationService(
                statementRepository,
                statementTradeRepository,
                ocrJobRepository,
                draftService,
                importTxService);
        return new OcrService(
                creationService,
                commandService,
                jobProcessor,
                queryService,
                draftService,
                confirmationService,
                viewMapper);
    }
}
