package tw.bk.appapi.stocks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tw.bk.appapi.stocks.vo.InstrumentResponse;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.model.MarketCode;
import tw.bk.appcommon.result.Result;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.appstocks.service.InstrumentService;

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

    private final InstrumentService instrumentService;

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
    @Operation(summary = "取得商品詳情", description = "根據 symbol_key 查詢商品詳細資訊")
    public Result<InstrumentResponse> getInstrument(@PathVariable String symbolKey) {
        validateSymbolKey(symbolKey);
        InstrumentEntity entity = instrumentService.findBySymbolKey(symbolKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + symbolKey));

        return Result.ok(InstrumentResponse.from(entity));
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
