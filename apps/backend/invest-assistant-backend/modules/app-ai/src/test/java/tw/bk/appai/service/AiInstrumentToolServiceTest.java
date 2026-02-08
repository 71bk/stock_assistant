package tw.bk.appai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.apppersistence.entity.ExchangeEntity;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.MarketEntity;
import tw.bk.appstocks.service.InstrumentService;

@ExtendWith(MockitoExtension.class)
class AiInstrumentToolServiceTest {

    @Mock
    private InstrumentService instrumentService;

    private AiInstrumentToolService service;

    @BeforeEach
    void setUp() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("instrumentSearch");
        service = new AiInstrumentToolService(instrumentService, cacheManager);
    }

    @Test
    void searchCandidates_shouldResolveSymbolKeyFromUrlParameter() {
        InstrumentEntity entity = instrument("TW:XTAI:2330", "2330", "台積電");
        when(instrumentService.findBySymbolKey("TW:XTAI:2330")).thenReturn(java.util.Optional.of(entity));

        List<InstrumentCandidate> candidates = service.searchCandidates(
                "http://localhost:8080/api/stocks/quote?symbolKey=TW:XTAI:2330",
                10);

        assertFalse(candidates.isEmpty());
        assertEquals("TW:XTAI:2330", candidates.get(0).symbolKey());
        verify(instrumentService).findBySymbolKey("TW:XTAI:2330");
        verify(instrumentService, never()).searchInstruments(anyString(), anyInt());
    }

    @Test
    void searchCandidates_shouldExtractHanTokenAfterStopwordRemoval() {
        InstrumentEntity entity = instrument("TW:XTAI:2330", "2330", "台積電");
        when(instrumentService.searchInstruments("台積電", 10)).thenReturn(List.of(entity));

        List<InstrumentCandidate> candidates = service.searchCandidates("台積電現在多少", 10);

        assertFalse(candidates.isEmpty());
        assertEquals("TW:XTAI:2330", candidates.get(0).symbolKey());
        verify(instrumentService).searchInstruments("台積電", 10);
        verify(instrumentService, never()).searchInstruments("台積電現在多少", 10);
    }

    private InstrumentEntity instrument(String symbolKey, String ticker, String nameZh) {
        MarketEntity market = new MarketEntity();
        market.setCode("TW");
        ExchangeEntity exchange = new ExchangeEntity();
        exchange.setCode("XTAI");

        InstrumentEntity entity = new InstrumentEntity();
        entity.setSymbolKey(symbolKey);
        entity.setTicker(ticker);
        entity.setNameZh(nameZh);
        entity.setAssetType("STOCK");
        entity.setMarket(market);
        entity.setExchange(exchange);
        return entity;
    }
}
