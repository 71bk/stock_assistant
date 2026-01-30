package tw.bk.appapi.ocr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.ocr.dto.CreateOcrJobRequest;
import tw.bk.appapi.ocr.dto.UpdateOcrDraftRequest;
import tw.bk.appapi.ocr.vo.OcrConfirmResponse;
import tw.bk.appapi.ocr.vo.OcrDraftListResponse;
import tw.bk.appapi.ocr.vo.OcrDraftResponse;
import tw.bk.appapi.ocr.vo.OcrJobResponse;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appocr.service.OcrService;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Slf4j
@RestController
@RequestMapping("/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR", description = "OCR import APIs")
public class OcrController {

    private static final TypeReference<List<String>> LIST_STRING = new TypeReference<>() {
    };

    private final OcrService ocrService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @PostMapping("/jobs")
    @Operation(summary = "Create OCR job")
    public Result<OcrJobResponse> createJob(@Valid @RequestBody CreateOcrJobRequest request) {
        log.info("接收到建立 OCR Job 請求: fileId={}, portfolioId={}, force={}",
                request.getFileId(), request.getPortfolioId(), request.getForce());
        Long userId = requireUserId();
        log.info("OCR Job userId={}", userId);
        Long fileId = parseId(request.getFileId());
        Long portfolioId = parseId(request.getPortfolioId());
        boolean force = Boolean.TRUE.equals(request.getForce());
        OcrJobEntity job = ocrService.createJob(userId, fileId, portfolioId, force);
        log.info("OCR Job 建立成功: jobId={}, status={}", job.getId(), job.getStatus());
        return Result.ok(OcrJobResponse.from(job));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get OCR job status")
    public Result<OcrJobResponse> getJob(@PathVariable String jobId) {
        Long userId = requireUserId();
        OcrJobEntity job = ocrService.getJob(userId, parseId(jobId));
        return Result.ok(OcrJobResponse.from(job));
    }

    @GetMapping("/jobs/{jobId}/drafts")
    @Operation(summary = "Get draft trades by job")
    public Result<OcrDraftListResponse> getDrafts(@PathVariable String jobId) {
        Long userId = requireUserId();
        List<StatementTradeEntity> drafts = ocrService.getDrafts(userId, parseId(jobId));
        List<OcrDraftResponse> items = drafts.stream()
                .map(draft -> OcrDraftResponse.from(
                        draft,
                        parseList(draft.getWarningsJson()),
                        parseList(draft.getErrorsJson())))
                .toList();
        return Result.ok(OcrDraftListResponse.builder().items(items).build());
    }

    @PatchMapping("/drafts/{draftId}")
    @Operation(summary = "Update draft trade")
    public Result<OcrDraftResponse> updateDraft(@PathVariable String draftId,
            @RequestBody UpdateOcrDraftRequest request) {
        Long userId = requireUserId();
        OcrDraftUpdate update = new OcrDraftUpdate(
                parseIdOrNull(request.getInstrumentId()),
                request.getRawTicker(),
                request.getName(),
                request.getTradeDate(),
                request.getSettlementDate(),
                request.getSide(),
                parseDecimalOrNull(request.getQuantity()),
                parseDecimalOrNull(request.getPrice()),
                request.getCurrency(),
                parseDecimalOrNull(request.getFee()),
                parseDecimalOrNull(request.getTax()));

        StatementTradeEntity updated = ocrService.updateDraft(userId, parseId(draftId), update);
        return Result.ok(OcrDraftResponse.from(
                updated,
                parseList(updated.getWarningsJson()),
                parseList(updated.getErrorsJson())));
    }

    @PostMapping("/jobs/{jobId}/confirm")
    @Operation(summary = "Confirm OCR import")
    public Result<OcrConfirmResponse> confirm(@PathVariable String jobId) {
        Long userId = requireUserId();
        int importedCount = ocrService.confirm(userId, parseId(jobId));
        return Result.ok(OcrConfirmResponse.builder().importedCount(importedCount).build());
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

    private Long parseIdOrNull(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null;
        }
        return parseId(idStr);
    }

    private BigDecimal parseDecimalOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid number format: " + value);
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_STRING);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
