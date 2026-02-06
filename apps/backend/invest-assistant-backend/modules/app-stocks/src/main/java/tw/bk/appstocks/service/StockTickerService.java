package tw.bk.appstocks.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.AssetType;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.InstrumentStatus;
import tw.bk.appcommon.enums.TickerType;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.enums.MarketCode;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.appstocks.model.TickerItem;
import tw.bk.appstocks.model.TickerList;
import tw.bk.appstocks.model.TickerQuery;
import tw.bk.appstocks.port.StockMarketClient;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockTickerService {

    private static final String STATUS_ACTIVE = InstrumentStatus.ACTIVE.name();

    private final List<StockMarketClient> stockMarketClients;
    private final InstrumentRepository instrumentRepository;
    private Map<MarketCode, StockMarketClient> clientMap = Collections.emptyMap();

    @PostConstruct
    void initClientMap() {
        clientMap = stockMarketClients.stream()
                .collect(Collectors.toUnmodifiableMap(
                        StockMarketClient::getSupportedMarket,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate market client: " + a.getSupportedMarket().getCode());
                        }
                ));
    }

    public TickerList getTickers(MarketCode marketCode, TickerQuery query) {
        StockMarketClient client = getClientByMarket(marketCode);
        return client.getTickers(query)
                .orElseGet(() -> buildFallbackList(marketCode, query));
    }

    private StockMarketClient getClientByMarket(MarketCode marketCode) {
        StockMarketClient client = clientMap.get(marketCode);
        if (client == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Unsupported market: " + marketCode.getCode());
        }

        return client;
    }

    private TickerList buildFallbackList(MarketCode marketCode, TickerQuery query) {
        List<InstrumentEntity> instruments = instrumentRepository.findAllWithRelations();

        List<TickerItem> items = instruments.stream()
                .filter(i -> i.getMarket() != null
                        && marketCode.getCode().equalsIgnoreCase(i.getMarket().getCode()))
                .filter(i -> matchesExchange(i, query.getExchange()))
                .filter(i -> matchesMarket(i, query.getMarket()))
                .filter(i -> matchesType(i, query.getType()))
                .filter(i -> STATUS_ACTIVE.equalsIgnoreCase(i.getStatus()))
                .map(this::toTickerItem)
                .collect(Collectors.toList());

        String resolvedMarket = query.getMarket();
        if (resolvedMarket == null || resolvedMarket.isBlank()) {
            resolvedMarket = marketCode.getCode();
        }

        return TickerList.builder()
                .date(LocalDate.now())
                .type(query.getType())
                .exchange(query.getExchange())
                .market(resolvedMarket)
                .industry(query.getIndustry())
                .isNormal(query.getIsNormal())
                .isAttention(query.getIsAttention())
                .isDisposition(query.getIsDisposition())
                .isHalted(query.getIsHalted())
                .data(items)
                .build();
    }

    private boolean matchesExchange(InstrumentEntity instrument, String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return true;
        }
        if (instrument.getExchange() == null || instrument.getExchange().getCode() == null) {
            return false;
        }
        return exchange.trim().equalsIgnoreCase(instrument.getExchange().getCode());
    }

    private boolean matchesMarket(InstrumentEntity instrument, String market) {
        if (market == null || market.isBlank()) {
            return true;
        }
        if (instrument.getMarket() == null || instrument.getMarket().getCode() == null) {
            return false;
        }
        return market.trim().equalsIgnoreCase(instrument.getMarket().getCode());
    }

    private boolean matchesType(InstrumentEntity instrument, String type) {
        if (type == null || type.isBlank()) {
            return true;
        }
        String assetType = instrument.getAssetType();
        if (assetType == null) {
            return false;
        }
        String normalized = type.trim().toUpperCase();
        TickerType tickerType = TickerType.from(normalized);
        if (tickerType == null) {
            return assetType.equalsIgnoreCase(normalized);
        }
        if (TickerType.EQUITY.equals(tickerType)) {
            return AssetType.STOCK.name().equalsIgnoreCase(assetType)
                    || AssetType.ETF.name().equalsIgnoreCase(assetType);
        }
        if (TickerType.INDEX.equals(tickerType)) {
            return "INDEX".equalsIgnoreCase(assetType);
        }
        if (TickerType.WARRANT.equals(tickerType)) {
            return AssetType.WARRANT.name().equalsIgnoreCase(assetType);
        }
        return assetType.equalsIgnoreCase(normalized);
    }

    private TickerItem toTickerItem(InstrumentEntity instrument) {
        String name = instrument.getNameZh();
        if (name == null || name.isBlank()) {
            name = instrument.getNameEn();
        }
        if (name == null || name.isBlank()) {
            name = instrument.getTicker();
        }
        return TickerItem.builder()
                .symbol(instrument.getTicker())
                .name(name)
                .build();
    }
}
