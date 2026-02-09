package tw.bk.appstocks.model;

import tw.bk.appcommon.enums.AssetType;

public record InstrumentView(
        Long id,
        String symbolKey,
        String ticker,
        String nameZh,
        String nameEn,
        String marketCode,
        String exchangeCode,
        String currency,
        AssetType assetType) {
}
