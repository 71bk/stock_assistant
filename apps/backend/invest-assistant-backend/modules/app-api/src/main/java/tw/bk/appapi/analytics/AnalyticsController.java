package tw.bk.appapi.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appanalytics.model.AnalyticsEvent;
import tw.bk.appanalytics.service.AnalyticsService;
import tw.bk.appapi.analytics.dto.AnalyticsEventBatchRequest;
import tw.bk.appapi.analytics.vo.AnalyticsIngestResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.ratelimit.RateLimiter;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analytics", description = "Authenticated product analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final CurrentUserProvider currentUserProvider;
    private final RateLimiter rateLimiter;
    private final int rateLimit;
    private final Duration rateWindow;

    public AnalyticsController(
            AnalyticsService analyticsService,
            CurrentUserProvider currentUserProvider,
            RateLimiter rateLimiter,
            @Value("${app.analytics.events.rate-limit:60}") int rateLimit,
            @Value("${app.analytics.events.rate-window:60s}") Duration rateWindow) {
        this.analyticsService = analyticsService;
        this.currentUserProvider = currentUserProvider;
        this.rateLimiter = rateLimiter;
        this.rateLimit = rateLimit;
        this.rateWindow = rateWindow;
    }

    @PostMapping("/events")
    @Operation(summary = "Record authenticated product analytics events")
    public Result<AnalyticsIngestResponse> recordEvents(
            @Valid @RequestBody AnalyticsEventBatchRequest request) {
        Long userId = currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
        if (!rateLimiter.tryAcquire("analytics:events:" + userId, rateLimit, rateWindow)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Analytics event rate limit exceeded");
        }

        int inserted = analyticsService.recordEvents(
                userId,
                request.getEvents().stream()
                        .map(event -> new AnalyticsEvent(
                                event.getEventId(),
                                event.getSessionId(),
                                event.getEventType(),
                                event.getRoute(),
                                event.getOccurredAt()))
                        .toList());
        return Result.ok(new AnalyticsIngestResponse(inserted));
    }
}
