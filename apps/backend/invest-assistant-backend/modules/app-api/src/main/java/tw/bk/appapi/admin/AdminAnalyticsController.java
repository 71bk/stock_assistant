package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appanalytics.model.AnalyticsModels.ApiTraffic;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsage;
import tw.bk.appanalytics.model.AnalyticsModels.DailyTrend;
import tw.bk.appanalytics.model.AnalyticsModels.PageMetric;
import tw.bk.appanalytics.model.AnalyticsModels.Summary;
import tw.bk.appanalytics.service.AnalyticsService;
import tw.bk.appcommon.result.Result;

@RestController
@RequestMapping("/admin/analytics")
@Tag(name = "Admin Analytics", description = "Site usage and API traffic analytics")
public class AdminAnalyticsController {
    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get registration and engagement summary")
    public Result<Summary> summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Taipei") String timezone) {
        return Result.ok(analyticsService.getSummary(from, to, timezone));
    }

    @GetMapping("/users/trend")
    @Operation(summary = "Get daily registration and engagement trend")
    public Result<List<DailyTrend>> userTrend(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Taipei") String timezone) {
        return Result.ok(analyticsService.getDailyTrend(from, to, timezone));
    }

    @GetMapping("/pages")
    @Operation(summary = "Get top SPA pages")
    public Result<List<PageMetric>> pages(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Taipei") String timezone) {
        return Result.ok(analyticsService.getTopPages(from, to, timezone));
    }

    @GetMapping("/api-traffic")
    @Operation(summary = "Get API request volume and latency from Prometheus")
    public Result<ApiTraffic> apiTraffic(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Taipei") String timezone) {
        return Result.ok(analyticsService.getApiTraffic(from, to, timezone));
    }

    @GetMapping("/ai-usage")
    @Operation(summary = "Get AI provider token, call, retry, and fallback metrics")
    public Result<AiUsage> aiUsage(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Taipei") String timezone) {
        return Result.ok(analyticsService.getAiUsage(from, to, timezone));
    }
}
