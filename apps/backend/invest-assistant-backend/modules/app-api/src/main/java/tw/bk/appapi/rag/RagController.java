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
import tw.bk.appapi.rag.vo.RagDocumentResponse;
import tw.bk.appapi.rag.vo.RagIngestResponse;
import tw.bk.appapi.rag.vo.RagQueryResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.service.FileService;
import tw.bk.apprag.client.AiWorkerIngestResponse;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;
import tw.bk.apprag.model.RagDocumentView;
import tw.bk.apprag.service.RagDocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
@Tag(name = "RAG", description = "RAG APIs")
@RequiredArgsConstructor
public class RagController {
    private static final int MAX_PAGE_SIZE = 100;

    private final AiWorkerRagClient ragClient;
    private final FileService fileService;
    private final CurrentUserProvider currentUserProvider;
    private final RagDocumentService ragDocumentService;

    @Value("${app.rag.max-file-size-mb:50}")
    private long maxFileSizeMb;

    @GetMapping("/documents")
    @Operation(summary = "List RAG documents")
    public Result<PageResponse<RagDocumentResponse>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = requireUserId();
        PageableInfo pageInfo = buildPageable(page, size);
        Page<RagDocumentView> pageResult = ragDocumentService.listByUserId(userId, pageInfo.pageable());

        return Result.ok(PageResponse.ok(
                pageResult.getContent().stream().map(RagDocumentResponse::from).toList(),
                pageInfo.page(),
                pageInfo.size(),
                pageResult.getTotalElements()));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete RAG document")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        Long userId = requireUserId();
        // Verify ownership first so we never delete foreign documents.
        ragDocumentService.getForUser(userId, id);
        ragClient.deleteDocument(userId, id);

        return Result.ok();
    }

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
        FileView file = fileService.getFileView(userId, id);
        String contentType = file.contentType();
        validateFileSize(file);
        FileProvider provider = fileService.resolveProvider(file);
        String fileName = file.objectKey();
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
        Integer topK = request.getTopK();
        if (topK != null && topK <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "topK must be greater than 0");
        }
        AiWorkerQueryResponse response = ragClient.query(
                userId,
                request.getQuery(),
                topK,
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

    private void validateFileSize(FileView file) {
        if (file == null || file.sizeBytes() == null) {
            return;
        }
        long limitBytes = maxFileSizeMb * 1024L * 1024L;
        if (limitBytes > 0 && file.sizeBytes() > limitBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File size exceeds limit");
        }
    }

    private record PageableInfo(Pageable pageable, int page, int size) {
    }

    private PageableInfo buildPageable(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by("createdAt").descending());
        return new PageableInfo(pageable, safePage, safeSize);
    }
}
