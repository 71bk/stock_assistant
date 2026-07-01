package tw.bk.appocr.service;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrJobView;

/** Public facade for OCR application use cases. */
@Service
public class OcrService {
    private final OcrJobCreationService creationService;
    private final OcrJobCommandService commandService;
    private final OcrJobProcessor jobProcessor;
    private final OcrJobQueryService queryService;
    private final OcrDraftService draftService;
    private final OcrConfirmationService confirmationService;
    private final OcrViewMapper viewMapper;

    public OcrService(
            OcrJobCreationService creationService,
            OcrJobCommandService commandService,
            OcrJobProcessor jobProcessor,
            OcrJobQueryService queryService,
            OcrDraftService draftService,
            OcrConfirmationService confirmationService,
            OcrViewMapper viewMapper) {
        this.creationService = creationService;
        this.commandService = commandService;
        this.jobProcessor = jobProcessor;
        this.queryService = queryService;
        this.draftService = draftService;
        this.confirmationService = confirmationService;
        this.viewMapper = viewMapper;
    }

    public OcrJobView createJob(Long userId, Long fileId, Long portfolioId, boolean force) {
        return creationService.createJob(userId, fileId, portfolioId, force);
    }

    public void processJob(Long userId, Long jobId) {
        jobProcessor.processJob(userId, jobId);
    }

    public OcrJobView submitPdfPassword(Long userId, Long jobId, String pdfPassword) {
        return commandService.submitPdfPassword(userId, jobId, pdfPassword);
    }

    public OcrJobView getJob(Long userId, Long jobId) {
        return queryService.getJob(userId, jobId);
    }

    public List<OcrDraftView> getDrafts(Long userId, Long jobId) {
        return queryService.getDrafts(userId, jobId);
    }

    public OcrJobView retryJob(Long userId, Long jobId, boolean force) {
        return commandService.retryJob(userId, jobId, force);
    }

    public OcrJobView reparse(Long userId, Long jobId, boolean force) {
        return commandService.reparse(userId, jobId, force);
    }

    public OcrJobView cancel(Long userId, Long jobId, boolean force) {
        return commandService.cancel(userId, jobId, force);
    }

    public Long getPortfolioIdByJob(Long userId, Long jobId) {
        return queryService.getPortfolioIdByJob(userId, jobId);
    }

    public Long getPortfolioIdByStatementId(Long userId, Long statementId) {
        return queryService.getPortfolioIdByStatementId(userId, statementId);
    }

    public OcrDraftView updateDraft(Long userId, Long draftId, OcrDraftUpdate update) {
        return viewMapper.toDraftView(draftService.updateDraft(userId, draftId, update));
    }

    public ConfirmResult confirm(Long userId, Long jobId, Set<Long> selectedDraftIds) {
        return confirmationService.confirm(userId, jobId, selectedDraftIds);
    }

    public void deleteDraft(Long userId, Long draftId) {
        confirmationService.deleteDraft(userId, draftId);
    }
}
