package tw.bk.appstocks.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.ExchangeEntity;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.MarketEntity;
import tw.bk.apppersistence.repository.ExchangeRepository;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.MarketRepository;
import tw.bk.appstocks.adapter.FugleClient;
import tw.bk.appstocks.adapter.dto.FugleTickerResponse;
import tw.bk.appstocks.model.TickerList;
import tw.bk.appstocks.model.TickerQuery;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentSyncService {
    private static final String MARKET_TW = "TW";
    private static final String EXCHANGE_TWSE = "TWSE";
    private static final String EXCHANGE_TPEX = "TPEx";
    private static final String ASSET_TYPE_STOCK = "STOCK";
    private static final String ASSET_TYPE_ETF = "ETF";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> ETF_SECURITY_TYPES = Set.of(
            "24", "25", "26", "28", "45", "46", "47", "48", "49");

    private final FugleClient fugleClient;
    private final MarketRepository marketRepository;
    private final ExchangeRepository exchangeRepository;
    private final InstrumentRepository instrumentRepository;

    public SyncResult syncTwEquityInstruments() {
        MarketEntity market = marketRepository.findByCode(MARKET_TW)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "TW market not found"));

        ExchangeEntity twse = exchangeRepository.findByMarketIdAndCodeIgnoreCase(market.getId(), EXCHANGE_TWSE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "TWSE exchange not found"));
        ExchangeEntity tpex = exchangeRepository.findByMarketIdAndCodeIgnoreCase(market.getId(), EXCHANGE_TPEX)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "TPEx exchange not found"));

        List<Seed> seeds = new ArrayList<>();
        log.info("Fetching TWSE instruments...");
        seeds.addAll(fetchSeeds(EXCHANGE_TWSE, market, twse));
        log.info("TWSE fetch complete. Waiting 60 seconds before fetching TPEx to avoid rate limiting...");

        // 延遲 60 秒以避免 Fugle API 速率限制 (429 Too Many Requests)
        try {
            Thread.sleep(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Sync interrupted");
        }

        log.info("Fetching TPEx instruments...");
        seeds.addAll(fetchSeeds(EXCHANGE_TPEX, market, tpex));

        if (seeds.isEmpty()) {
            return new SyncResult(0, 0);
        }

        Set<String> allKeys = seeds.stream()
                .map(seed -> buildSymbolKey(market.getCode(), seed.exchange().getMic(), seed.ticker()))
                .collect(Collectors.toSet());

        Set<String> existingKeys = instrumentRepository.findBySymbolKeyIn(new ArrayList<>(allKeys)).stream()
                .map(InstrumentEntity::getSymbolKey)
                .collect(Collectors.toSet());

        int added = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();

        for (Seed seed : seeds) {
            String symbolKey = buildSymbolKey(market.getCode(), seed.exchange().getMic(), seed.ticker());
            if (!seen.add(symbolKey)) {
                skipped++;
                continue;
            }
            if (existingKeys.contains(symbolKey)) {
                skipped++;
                continue;
            }

            InstrumentEntity entity = new InstrumentEntity();
            entity.setMarket(seed.market());
            entity.setExchange(seed.exchange());
            entity.setTicker(seed.ticker());
            entity.setSymbolKey(symbolKey);
            entity.setNameZh(seed.name());
            entity.setNameEn(null);
            entity.setCurrency(seed.market().getDefaultCurrency());
            entity.setAssetType(ASSET_TYPE_STOCK);
            entity.setStatus(STATUS_ACTIVE);
            // 註釋掉以避免 Fugle API 限流 (429 Too Many Requests)
            // 基本資料已足夠，詳細資料可之後單獨獲取
            // applyTickerDetail(entity, seed.ticker());
            try {
                instrumentRepository.save(entity);
                added++;
            } catch (DataIntegrityViolationException ex) {
                skipped++;
                log.debug("Instrument already exists, skip: {}", symbolKey);
            }
        }

        log.info("Instrument sync done: added={}, skipped={}", added, skipped);
        return new SyncResult(added, skipped);
    }

    private List<Seed> fetchSeeds(String exchangeCode, MarketEntity market, ExchangeEntity exchange) {
        TickerQuery query = TickerQuery.builder()
                .type("EQUITY")
                .exchange(exchangeCode)
                .build();

        TickerList list = fugleClient.getTickers(query)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Fugle tickers empty for exchange: " + exchangeCode));

        if (list.getData() == null || list.getData().isEmpty()) {
            return List.of();
        }

        return list.getData().stream()
                .filter(item -> item.getSymbol() != null && !item.getSymbol().isBlank())
                .map(item -> new Seed(
                        market,
                        exchange,
                        item.getSymbol().trim().toUpperCase(Locale.ROOT),
                        item.getName()))
                .toList();
    }

    private String buildSymbolKey(String marketCode, String mic, String ticker) {
        return marketCode + ":" + mic + ":" + ticker;
    }

    private void applyTickerDetail(InstrumentEntity entity, String ticker) {
        FugleTickerResponse detail = fugleClient.getTickerDetail(ticker).orElse(null);
        if (detail == null) {
            return;
        }
        if (detail.getNameEn() != null && !detail.getNameEn().isBlank()) {
            entity.setNameEn(detail.getNameEn().trim());
        }
        if (detail.getTradingCurrency() != null && !detail.getTradingCurrency().isBlank()) {
            entity.setCurrency(detail.getTradingCurrency().trim().toUpperCase(Locale.ROOT));
        }
        if (isEtf(detail.getSecurityType())) {
            entity.setAssetType(ASSET_TYPE_ETF);
        }
    }

    private boolean isEtf(String securityType) {
        if (securityType == null || securityType.isBlank()) {
            return false;
        }
        return ETF_SECURITY_TYPES.contains(securityType.trim());
    }

    private record Seed(MarketEntity market, ExchangeEntity exchange, String ticker, String name) {
    }

    public record SyncResult(int added, int skipped) {
    }
}
