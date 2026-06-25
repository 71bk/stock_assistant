package tw.bk.appanalytics.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class AnalyticsModels {
    private AnalyticsModels() {
    }

    public record Summary(
            long totalUsers,
            long newUsers,
            long dau,
            long wau,
            long mau,
            long pageViews,
            long sessions) {
    }

    public record DailyTrend(
            LocalDate date,
            long newUsers,
            long activeUsers,
            long pageViews) {
    }

    public record PageMetric(
            String route,
            long views,
            long uniqueUsers,
            long sessions) {
    }

    public record ApiTraffic(
            boolean available,
            long requestCount,
            long clientErrorCount,
            long serverErrorCount,
            double errorRatePercent,
            double p95LatencyMs,
            List<ApiEndpointMetric> topEndpoints,
            List<ApiTrafficPoint> trend,
            String warning) {

        public static ApiTraffic unavailable(String warning) {
            return new ApiTraffic(false, 0, 0, 0, 0, 0, List.of(), List.of(), warning);
        }
    }

    public record ApiEndpointMetric(
            String method,
            String uri,
            long requestCount) {
    }

    public record ApiTrafficPoint(
            OffsetDateTime time,
            double requestCount) {
    }

    public record AiUsage(
            boolean available,
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            long callCount,
            long failedCallCount,
            double failureRatePercent,
            double p95DurationMs,
            long ocrFallbackCount,
            long ocrRetryCount,
            long embeddingRetryCount,
            long embeddingBatchFallbackCount,
            List<AiUsageBreakdown> byProvider,
            List<AiUsageBreakdown> byOperation,
            List<AiModelUsage> models,
            List<AiTokenTrendPoint> trend,
            String warning) {

        public static AiUsage unavailable(String warning) {
            return new AiUsage(
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    warning);
        }
    }

    public record AiUsageBreakdown(
            String key,
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            long callCount,
            long failedCallCount) {

        public long totalTokens() {
            return inputTokens + outputTokens + cachedTokens;
        }
    }

    public record AiModelUsage(
            String provider,
            String model,
            String operation,
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            long callCount,
            long failedCallCount) {

        public long totalTokens() {
            return inputTokens + outputTokens + cachedTokens;
        }
    }

    public record AiTokenTrendPoint(
            OffsetDateTime time,
            double inputTokens,
            double outputTokens,
            double cachedTokens) {
    }
}
