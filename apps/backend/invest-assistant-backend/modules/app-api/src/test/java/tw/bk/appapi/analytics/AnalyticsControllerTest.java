package tw.bk.appapi.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appanalytics.service.AnalyticsService;
import tw.bk.appapi.analytics.dto.AnalyticsEventBatchRequest;
import tw.bk.appapi.analytics.dto.AnalyticsEventRequest;
import tw.bk.appapi.analytics.vo.AnalyticsIngestResponse;
import tw.bk.appcommon.ratelimit.RateLimiter;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {
    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private RateLimiter rateLimiter;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(
                analyticsService,
                currentUserProvider,
                rateLimiter,
                60,
                Duration.ofMinutes(1));
    }

    @Test
    void recordEvents_shouldBindAuthenticatedUserAndReturnAcceptedCount() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(9L));
        when(rateLimiter.tryAcquire("analytics:events:9", 60, Duration.ofMinutes(1)))
                .thenReturn(true);
        when(analyticsService.recordEvents(org.mockito.ArgumentMatchers.eq(9L), anyList()))
                .thenReturn(1);

        AnalyticsEventRequest event = new AnalyticsEventRequest();
        event.setEventId(UUID.randomUUID());
        event.setSessionId(UUID.randomUUID());
        event.setEventType("PAGE_VIEW");
        event.setRoute("/dashboard");
        event.setOccurredAt(OffsetDateTime.now());
        AnalyticsEventBatchRequest request = new AnalyticsEventBatchRequest();
        request.setEvents(List.of(event));

        Result<AnalyticsIngestResponse> result = controller.recordEvents(request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().accepted());
        verify(analyticsService).recordEvents(org.mockito.ArgumentMatchers.eq(9L), anyList());
    }
}
