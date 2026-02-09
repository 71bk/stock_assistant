package tw.bk.appapi.files;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tw.bk.appapi.files.dto.PresignRequest;
import tw.bk.appapi.files.vo.FileResponse;
import tw.bk.appapi.files.vo.FileUrlResponse;
import tw.bk.appapi.files.vo.PresignResponse;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.model.PresignResult;
import tw.bk.appfiles.service.FileService;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File upload and metadata")
public class FilesController {

    private final FileService fileService;
    private final CurrentUserProvider currentUserProvider;

    @org.springframework.beans.factory.annotation.Value("${app.files.presign-expiry-seconds:900}")
    private int presignExpirySeconds;

    @PostMapping
    @Operation(summary = "Upload file")
    public Result<FileResponse> upload(@RequestParam("file") MultipartFile file) {
        log.info("接收到檔案上傳請求: filename={}, size={}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "檔案不得為空");
        }
        Long userId = requireUserId();
        log.info("檔案上傳 userId={}", userId);
        FileView entity;
        try {
            entity = fileService.uploadView(userId, file.getContentType(), file.getInputStream());
        } catch (Exception ex) {
            if (ex instanceof BusinessException) {
                throw (BusinessException) ex;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "檔案儲存失敗");
        }
        return Result.ok(FileResponse.from(entity));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Get file metadata")
    public Result<FileResponse> getMetadata(@PathVariable String fileId) {
        Long userId = requireUserId();
        FileView entity = fileService.getFileView(userId, parseId(fileId));
        return Result.ok(FileResponse.from(entity));
    }

    @GetMapping("/{fileId}/url")
    @Operation(summary = "Get file download/preview URL")
    public Result<FileUrlResponse> getFileUrl(@PathVariable String fileId) {
        Long userId = requireUserId();
        Long id = parseId(fileId);
        FileView file = fileService.getFileView(userId, id);
        FileProvider provider = fileService.resolveProvider(file);

        if (provider == FileProvider.LOCAL) {
            return Result.ok(FileUrlResponse.builder()
                    .url("/api/files/" + id + "/content")
                    .expiresAt(null)
                    .build());
        }

        String url = fileService.presignDownloadUrl(file);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(Math.max(1, presignExpirySeconds));
        return Result.ok(FileUrlResponse.builder()
                .url(url)
                .expiresAt(expiresAt)
                .build());
    }

    @GetMapping("/{fileId}/content")
    @Operation(summary = "Stream file content (local provider fallback)")
    public ResponseEntity<byte[]> getContent(@PathVariable String fileId) {
        Long userId = requireUserId();
        Long id = parseId(fileId);
        FileView file = fileService.getFileView(userId, id);
        byte[] bytes = fileService.loadBytes(userId, id);
        MediaType mediaType = parseMediaType(file.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(bytes);
    }

    @PostMapping("/presign")
    @Operation(summary = "Get presigned upload URL")
    public Result<PresignResponse> presign(@Valid @RequestBody PresignRequest request) {
        Long userId = requireUserId();
        PresignResult result = fileService.presignUpload(
                userId,
                request.getSha256(),
                request.getSizeBytes(),
                request.getContentType());
        return Result.ok(PresignResponse.from(result));
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

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
