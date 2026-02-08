package tw.bk.appapi.rag;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.rag.dto.CreateRagDocumentRequest;
import tw.bk.appapi.rag.dto.RagQueryRequest;
import tw.bk.appapi.rag.vo.RagIngestResponse;
import tw.bk.appapi.rag.vo.RagQueryResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appfiles.service.FileService;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apprag.client.AiWorkerIngestResponse;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;

@RestController
@RequestMapping("/rag")
@Tag(name = "RAG", description = "RAG APIs")
@RequiredArgsConstructor
public class RagController {
    private final AiWorkerRagClient ragClient;
    private final FileService fileService;
    private final CurrentUserProvider currentUserProvider;

    @Value("${app.rag.max-file-size-mb:50}")
    private long maxFileSizeMb;

    @PostMapping("/documents")
    @Operation(summary = "Create RAG document")
    public Result<RagIngestResponse> ingest(@RequestBody CreateRagDocumentRequest request) {
        Long userId = requireUserId();
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request body is required");
        }

        String rawText = request.getRawText();
        String fileId = request.getFileId();
        String title = request.getTitle();
        String sourceType = request.getSourceType();

        boolean hasText = rawText != null && !rawText.isBlank();
        boolean hasFile = fileId != null && !fileId.isBlank();
        if (hasText == hasFile) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Provide either rawText or fileId");
        }

        if (hasText) {
            if (title == null || title.isBlank()) {
                title = "note";
            }
            AiWorkerIngestResponse response = ragClient.ingestText(
                    userId, rawText, title, sourceType, request.getTags());
            return Result.ok(RagIngestResponse.from(response));
        }

        Long id = parseId(fileId);
        FileEntity file = fileService.getFile(userId, id);
        String contentType = file.getContentType();
        validateFileSize(file);
        FileProvider provider = fileService.resolveProvider(file);
        String fileName = file.getObjectKey();
        if (title == null || title.isBlank()) {
            title = fileName;
        }

        AiWorkerIngestResponse response;
        if (provider == FileProvider.LOCAL) {
            byte[] content = fileService.loadBytes(file);
            response = ragClient.ingestFile(
                    userId, fileName, contentType, content, title, sourceType, request.getTags());
        } else {
            String fileUrl = fileService.presignDownloadUrl(file);
            response = ragClient.ingestUrl(
                    userId, fileUrl, fileName, contentType, title, sourceType, request.getTags());
        }
        return Result.ok(RagIngestResponse.from(response));
    }

    @PostMapping("/query")
    @Operation(summary = "RAG query")
    public Result<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        Long userId = requireUserId();
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request body is required");
        }
        AiWorkerQueryResponse response = ragClient.query(
                userId,
                request.getQuery(),
                request.getTopK(),
                request.getSourceType());
        return Result.ok(RagQueryResponse.from(response));
    }

    private Long requireUserId() {
        return currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
    }

    private Long parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid ID format");
        }
    }

    private void validateFileSize(FileEntity file) {
        if (file == null || file.getSizeBytes() == null) {
            return;
        }
        long limitBytes = maxFileSizeMb * 1024L * 1024L;
        if (limitBytes > 0 && file.getSizeBytes() > limitBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File size exceeds limit");
        }
    }
}
