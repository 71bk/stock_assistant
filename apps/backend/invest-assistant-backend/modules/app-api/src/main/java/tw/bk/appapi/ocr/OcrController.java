package tw.bk.appapi.ocr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.ocr.dto.ConfirmOcrRequest;
import tw.bk.appapi.ocr.dto.CreateOcrJobRequest;
import tw.bk.appapi.ocr.dto.UpdateOcrDraftRequest;
import tw.bk.appapi.ocr.vo.OcrConfirmResponse;
import tw.bk.appapi.ocr.vo.OcrConfirmResponse.DraftError;
import tw.bk.appapi.ocr.vo.OcrDraftListResponse;
import tw.bk.appapi.ocr.vo.OcrDraftResponse;
import tw.bk.appapi.ocr.vo.OcrJobResponse;
import tw.bk.appcommon.enums.ErrorCode;
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

    @RequestMapping(value = "/drafts/{draftId}", method = { org.springframework.web.bind.annotation.RequestMethod.PUT,
            org.springframework.web.bind.annotation.RequestMethod.PATCH })
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
    @Operation(summary = "Confirm OCR import", description = "Import selected drafts. If draftIds is null/empty, imports all drafts.")
    public Result<OcrConfirmResponse> confirm(
            @PathVariable String jobId,
            @RequestBody(required = false) ConfirmOcrRequest request) {
        Long userId = requireUserId();
        Set<Long> selectedIds = null;
        if (request != null && request.getDraftIds() != null && !request.getDraftIds().isEmpty()) {
            selectedIds = new HashSet<>();
            for (String id : request.getDraftIds()) {
                selectedIds.add(parseId(id));
            }
        }
        var result = ocrService.confirm(userId, parseId(jobId), selectedIds);

        // Convert ConfirmResult to OcrConfirmResponse
        List<DraftError> errors = result.getErrors() != null
                ? result.getErrors().stream()
                        .map(e -> DraftError.builder()
                                .draftId(String.valueOf(e.getDraftId()))
                                .reason(e.getReason())
                                .build())
                        .toList()
                : List.of();

        return Result.ok(OcrConfirmResponse.builder()
                .importedCount(result.getImportedCount())
                .errors(errors)
                .build());
    }

    
    @PostMapping("/jobs/{jobId}/reparse")
    @Operation(summary = "Reparse OCR job")
    public Result<OcrJobResponse> reparse(
            @PathVariable String jobId,
            @RequestParam(required = false) Boolean force) {
        Long userId = requireUserId();
        boolean isForce = Boolean.TRUE.equals(force);
        OcrJobEntity job = ocrService.reparse(userId, parseId(jobId), isForce);
        return Result.ok(OcrJobResponse.from(job));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel OCR job")
    public Result<OcrJobResponse> cancel(
            @PathVariable String jobId,
            @RequestParam(required = false) Boolean force) {
        Long userId = requireUserId();
        boolean isForce = Boolean.TRUE.equals(force);
        OcrJobEntity job = ocrService.cancel(userId, parseId(jobId), isForce);
        return Result.ok(OcrJobResponse.from(job));
    }

@DeleteMapping("/drafts/{draftId}")
    @Operation(summary = "Delete draft trade")
    public Result<Void> deleteDraft(@PathVariable String draftId) {
        Long userId = requireUserId();
        ocrService.deleteDraft(userId, parseId(draftId));
        return Result.ok();
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
