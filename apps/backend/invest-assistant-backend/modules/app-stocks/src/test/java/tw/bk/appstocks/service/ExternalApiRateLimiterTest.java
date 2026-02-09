package tw.bk.appstocks.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.config.StockMarketProperties;

@ExtendWith(MockitoExtension.class)
class ExternalApiRateLimiterTest {

    @Mock
    private StockMetricsRecorder metricsRecorder;

    private StockMarketProperties properties;
    private ExternalApiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new StockMarketProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setWindowMs(1000);
        properties.getRateLimit().setAlpacaLimit(1);
        properties.getRateLimit().setFugleLimit(1);
        limiter = new ExternalApiRateLimiter(properties, metricsRecorder);
    }

    @Test
    void acquire_shouldThrowWhenExceedLimit() {
        limiter.acquire("alpaca", "quote");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> limiter.acquire("alpaca", "quote"));

        assertEquals(ErrorCode.RATE_LIMITED, ex.getErrorCode());
        verify(metricsRecorder).recordRateLimitBlocked("alpaca", "quote");
    }

    @Test
    void acquire_shouldSkipWhenLimiterDisabled() {
        properties.getRateLimit().setEnabled(false);

        assertDoesNotThrow(() -> limiter.acquire("alpaca", "quote"));
        assertDoesNotThrow(() -> limiter.acquire("alpaca", "quote"));
    }
}
