package tw.bk.appapi.files;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tw.bk.appapi.files.vo.FileResponse;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appfiles.service.FileService;
import tw.bk.apppersistence.entity.FileEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import tw.bk.appapi.files.dto.PresignRequest;
import tw.bk.appapi.files.vo.PresignResponse;
import tw.bk.appfiles.model.PresignResult;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File upload and metadata")
public class FilesController {

    private final FileService fileService;
    private final CurrentUserProvider currentUserProvider;

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
        FileEntity entity;
        try {
            entity = fileService.upload(userId, file.getContentType(), file.getInputStream());
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
        FileEntity entity = fileService.getFile(userId, parseId(fileId));
        return Result.ok(FileResponse.from(entity));
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
}
