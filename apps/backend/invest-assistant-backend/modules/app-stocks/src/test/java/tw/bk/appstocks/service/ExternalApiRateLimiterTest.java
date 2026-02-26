package tw.bk.appstocks.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.ratelimit.RateLimiter;
import tw.bk.appstocks.config.StockMarketProperties;

@ExtendWith(MockitoExtension.class)
class ExternalApiRateLimiterTest {

    @Mock
    private StockMetricsRecorder metricsRecorder;

    @Mock
    private RateLimiter rateLimiter;

    private StockMarketProperties properties;
    private ExternalApiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new StockMarketProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setWindowMs(1000);
        properties.getRateLimit().setAlpacaLimit(1);
        properties.getRateLimit().setFugleLimit(1);
        limiter = new ExternalApiRateLimiter(properties, metricsRecorder, rateLimiter);
    }

    @Test
    void acquire_shouldThrowWhenExceedLimit() {
        when(rateLimiter.tryAcquire("stock:external:alpaca:quote", 1, java.time.Duration.ofMillis(1000)))
                .thenReturn(true)
                .thenReturn(false);

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
