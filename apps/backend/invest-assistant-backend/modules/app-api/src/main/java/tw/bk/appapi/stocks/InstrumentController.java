package tw.bk.appapi.stocks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appapi.stocks.dto.CreateInstrumentRequest;
import tw.bk.appapi.stocks.vo.InstrumentResponse;
import tw.bk.appapi.stocks.vo.InstrumentDetailResponse;
import tw.bk.appapi.stocks.vo.EtfProfileResponse;

import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.model.MarketCode;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.appstocks.service.InstrumentService;
import tw.bk.appstocks.service.EtfProfileService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品主檔 API
 */
@RestController
@RequestMapping("/instruments")
@RequiredArgsConstructor
@Tag(name = "Instruments", description = "商品主檔管理")
public class InstrumentController {

    private static final int MAX_PAGE_SIZE = 20;

    private final InstrumentService instrumentService;
    private final EtfProfileService etfProfileService;

    /**
     * 手動建立商品
     */
    @PostMapping
    @Operation(summary = "建立商品", description = "手動建立一個新的商品（用於 Fugle 沒有的標的）")
    public Result<InstrumentResponse> createInstrument(@Valid @RequestBody CreateInstrumentRequest request) {
        InstrumentEntity entity = instrumentService.createInstrument(
                request.getTicker(),
                request.getNameZh(),
                request.getNameEn(),
                request.getMarket(),
                request.getExchange(),
                request.getCurrency(),
                request.getAssetType());
        return Result.ok(InstrumentResponse.from(entity));
    }

    /**
     * 搜尋商品（自動補全用）
     */
    @GetMapping("/search")
    @Operation(summary = "搜尋商品", description = "根據 ticker 或名稱搜尋商品，用於前端自動補全")
    public Result<List<InstrumentResponse>> searchInstruments(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {

        List<InstrumentEntity> entities = instrumentService.searchInstruments(q, limit);
        List<InstrumentResponse> responses = entities.stream()
                .map(InstrumentResponse::from)
                .collect(Collectors.toList());

        return Result.ok(responses);
    }

    /**
     * 取得單一商品詳情
     */
    @GetMapping("/{symbolKey}")
    @Operation(summary = "取得商品詳情", description = "根據 symbol_key 查詢商品詳細資訊，ETF 會額外回傳標的資訊")
    public Result<InstrumentDetailResponse> getInstrument(@PathVariable String symbolKey) {
        validateSymbolKey(symbolKey);
        InstrumentEntity entity = instrumentService.findBySymbolKey(symbolKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + symbolKey));

        InstrumentResponse instrument = InstrumentResponse.from(entity);

        // 如果是 ETF，查詢 ETF profile
        EtfProfileResponse etfProfile = null;
        if ("ETF".equals(entity.getAssetType())) {
            etfProfile = etfProfileService.findByInstrumentId(entity.getId())
                    .map(EtfProfileResponse::from)
                    .orElse(null);
        }

        return Result.ok(InstrumentDetailResponse.of(instrument, etfProfile));
    }

    /**
     * 取得所有商品（慎用，資料量大時建議改用查詢或分頁）
     */
    @GetMapping
    @Operation(summary = "取得所有商品", description = "回傳完整商品清單")
    public Result<PageResponse<InstrumentResponse>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page) - 1;
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<InstrumentEntity> entities = instrumentService.findAll(pageable);
        List<InstrumentResponse> responses = entities.getContent().stream()
                .map(InstrumentResponse::from)
                .collect(Collectors.toList());
        return Result.ok(PageResponse.ok(responses, safePage + 1, safeSize, entities.getTotalElements()));
    }

    private void validateSymbolKey(String symbolKey) {
        if (symbolKey == null || symbolKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "symbol_key 為必填");
        }
        String[] parts = symbolKey.split(":", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "symbol_key 格式錯誤: " + symbolKey);
        }
        MarketCode.requireSupported(parts[0], ErrorCode.VALIDATION_ERROR, "不支援的市場: " + parts[0]);
    }
}
