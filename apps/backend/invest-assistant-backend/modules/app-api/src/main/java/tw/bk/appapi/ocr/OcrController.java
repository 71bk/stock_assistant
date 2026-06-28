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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.ocr.dto.ConfirmOcrRequest;
import tw.bk.appapi.ocr.dto.CreateOcrJobRequest;
import tw.bk.appapi.ocr.dto.SubmitOcrPasswordRequest;
import tw.bk.appapi.ocr.dto.UpdateOcrDraftRequest;
import tw.bk.appapi.ocr.vo.OcrConfirmResponse;
import tw.bk.appapi.ocr.vo.OcrDraftListResponse;
import tw.bk.appapi.ocr.vo.OcrDraftResponse;
import tw.bk.appapi.ocr.vo.OcrJobResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appapi.web.CurrentUser;
import tw.bk.appapi.web.IdParser;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.service.OcrService;

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
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @PostMapping("/jobs")
    @Operation(summary = "Create OCR job")
    public Result<OcrJobResponse> createJob(@Valid @RequestBody CreateOcrJobRequest request) {
        log.info("Create OCR job: fileId={}, portfolioId={}, force={}",
                request.getFileId(), request.getPortfolioId(), request.getForce());
        Long userId = CurrentUser.require(currentUserProvider);
        Long fileId = IdParser.parseId(request.getFileId());
        Long portfolioId = IdParser.parseId(request.getPortfolioId());
        boolean force = Boolean.TRUE.equals(request.getForce());
        OcrJobView job = ocrService.createJob(userId, fileId, portfolioId, force);
        log.info("Created OCR job: jobId={}, status={}", job.id(), job.status());
        return Result.ok(OcrJobResponse.from(job));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get OCR job status")
    public Result<OcrJobResponse> getJob(@PathVariable String jobId) {
        Long userId = CurrentUser.require(currentUserProvider);
        OcrJobView job = ocrService.getJob(userId, IdParser.parseId(jobId));
        return Result.ok(OcrJobResponse.from(job));
    }

    @PostMapping("/jobs/{jobId}/password")
    @Operation(summary = "Submit PDF password for OCR job")
    public Result<OcrJobResponse> submitPassword(
            @PathVariable String jobId,
            @Valid @RequestBody SubmitOcrPasswordRequest request) {
        Long userId = CurrentUser.require(currentUserProvider);
        Long parsedJobId = IdParser.parseId(jobId);
        log.info("Submit OCR PDF password: jobId={}, passwordProvided=true", parsedJobId);
        OcrJobView job = ocrService.submitPdfPassword(userId, parsedJobId, request.getPassword());
        return Result.ok(OcrJobResponse.from(job));
    }

    @GetMapping("/jobs/{jobId}/drafts")
    @Operation(summary = "Get draft trades by job")
    public Result<OcrDraftListResponse> getDrafts(@PathVariable String jobId) {
        Long userId = CurrentUser.require(currentUserProvider);
        List<OcrDraftView> drafts = ocrService.getDrafts(userId, IdParser.parseId(jobId));
        List<OcrDraftResponse> items = drafts.stream()
                .map(draft -> OcrDraftResponse.from(
                        draft,
                        parseList(draft.warningsJson()),
                        parseList(draft.errorsJson())))
                .toList();
        return Result.ok(OcrDraftListResponse.builder().items(items).build());
    }

    @RequestMapping(value = "/drafts/{draftId}", method = { RequestMethod.PUT, RequestMethod.PATCH })
    @Operation(summary = "Update draft trade")
    public Result<OcrDraftResponse> updateDraft(
            @PathVariable String draftId,
            @RequestBody UpdateOcrDraftRequest request) {
        Long userId = CurrentUser.require(currentUserProvider);
        OcrDraftUpdate update = new OcrDraftUpdate(
                IdParser.parseIdOrNull(request.getInstrumentId()),
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

        OcrDraftView updated = ocrService.updateDraft(userId, IdParser.parseId(draftId), update);
        return Result.ok(OcrDraftResponse.from(
                updated,
                parseList(updated.warningsJson()),
                parseList(updated.errorsJson())));
    }

    @PostMapping("/jobs/{jobId}/confirm")
    @Operation(summary = "Confirm OCR import", description = "Import selected drafts. If draftIds is null or empty, imports all drafts.")
    public Result<OcrConfirmResponse> confirm(
            @PathVariable String jobId,
            @RequestBody(required = false) ConfirmOcrRequest request) {
        Long userId = CurrentUser.require(currentUserProvider);
        Set<Long> selectedIds = null;
        if (request != null && request.getDraftIds() != null && !request.getDraftIds().isEmpty()) {
            selectedIds = new HashSet<>();
            for (String id : request.getDraftIds()) {
                selectedIds.add(IdParser.parseId(id));
            }
        }
        var result = ocrService.confirm(userId, IdParser.parseId(jobId), selectedIds);
        return Result.ok(OcrConfirmResponse.from(result));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(summary = "Retry OCR job")
    public Result<OcrJobResponse> retry(
            @PathVariable String jobId,
            @RequestParam(required = false) Boolean force) {
        Long userId = CurrentUser.require(currentUserProvider);
        boolean isForce = Boolean.TRUE.equals(force);
        OcrJobView job = ocrService.retryJob(userId, IdParser.parseId(jobId), isForce);
        recordOcrRetry(isForce ? "force_retry" : "manual_retry");
        return Result.ok(OcrJobResponse.from(job));
    }

    @PostMapping("/jobs/{jobId}/reparse")
    @Operation(summary = "Reparse OCR job")
    public Result<OcrJobResponse> reparse(
            @PathVariable String jobId,
            @RequestParam(required = false) Boolean force) {
        Long userId = CurrentUser.require(currentUserProvider);
        boolean isForce = Boolean.TRUE.equals(force);
        OcrJobView job = ocrService.reparse(userId, IdParser.parseId(jobId), isForce);
        recordOcrRetry(isForce ? "force_reparse" : "manual_reparse");
        return Result.ok(OcrJobResponse.from(job));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel OCR job")
    public Result<OcrJobResponse> cancel(
            @PathVariable String jobId,
            @RequestParam(required = false) Boolean force) {
        Long userId = CurrentUser.require(currentUserProvider);
        boolean isForce = Boolean.TRUE.equals(force);
        OcrJobView job = ocrService.cancel(userId, IdParser.parseId(jobId), isForce);
        return Result.ok(OcrJobResponse.from(job));
    }

    @DeleteMapping("/drafts/{draftId}")
    @Operation(summary = "Delete draft trade")
    public Result<Void> deleteDraft(@PathVariable String draftId) {
        Long userId = CurrentUser.require(currentUserProvider);
        ocrService.deleteDraft(userId, IdParser.parseId(draftId));
        return Result.ok();
    }

    private void recordOcrRetry(String reason) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder("ocr_retry")
                .description("OCR retry or reparse requests")
                .tag("reason", reason)
                .register(registry)
                .increment();
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
