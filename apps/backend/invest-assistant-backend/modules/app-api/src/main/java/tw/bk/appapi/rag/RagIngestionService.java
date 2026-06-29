package tw.bk.appapi.rag;

import tw.bk.appapi.rag.dto.CreateRagDocumentRequest;
import tw.bk.appapi.web.IdParser;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.service.FileService;
import tw.bk.apprag.client.AiWorkerIngestResponse;
import tw.bk.apprag.client.AiWorkerRagClient;

/**
 * RAG 文件匯入：把 raw text 或既有檔案送進 AI worker 建索引。
 *
 * <p>從 {@code RagController} 抽出，集中「文字 vs 檔案」分流、檔案大小驗證，以及
 * 依儲存 provider 決定要直接送 bytes（local）或送 presigned URL（S3/MinIO）的差異，
 * 讓 controller 不必認得 storage provider 細節。
 */
class RagIngestionService {

    private final AiWorkerRagClient ragClient;
    private final FileService fileService;

    RagIngestionService(AiWorkerRagClient ragClient, FileService fileService) {
        this.ragClient = ragClient;
        this.fileService = fileService;
    }

    AiWorkerIngestResponse ingest(Long userId, CreateRagDocumentRequest request, long maxFileSizeBytes) {
        String rawText = request.getRawText();
        String fileId = request.getFileId();
        String title = request.getTitle();
        String sourceType = request.getSourceType();

        boolean hasText = rawText != null && !rawText.isBlank();
        boolean hasFile = fileId != null && !fileId.isBlank();
        if (hasText == hasFile) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Provide either rawText or fileId");
        }

        if (hasText) {
            if (title == null || title.isBlank()) {
                title = "note";
            }
            return ragClient.ingestText(userId, rawText, title, sourceType, request.getTags());
        }

        Long id = IdParser.parseId(fileId);
        FileView file = fileService.getFileView(userId, id);
        String contentType = file.contentType();
        validateFileSize(file, maxFileSizeBytes);
        FileProvider provider = fileService.resolveProvider(file);
        String fileName = file.objectKey();
        if (title == null || title.isBlank()) {
            title = fileName;
        }

        if (provider == FileProvider.LOCAL) {
            byte[] content = fileService.loadBytes(file);
            return ragClient.ingestFile(
                    userId, fileName, contentType, content, title, sourceType, request.getTags());
        }
        String fileUrl = fileService.presignDownloadUrl(file);
        return ragClient.ingestUrl(
                userId, fileUrl, fileName, contentType, title, sourceType, request.getTags());
    }

    private void validateFileSize(FileView file, long limitBytes) {
        if (file == null || file.sizeBytes() == null) {
            return;
        }
        if (limitBytes > 0 && file.sizeBytes() > limitBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File size exceeds limit");
        }
    }
}
