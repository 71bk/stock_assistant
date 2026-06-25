package tw.bk.appanalytics.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsProperties {
    private int maxRangeDays = 90;
    private int topPagesLimit = 10;
    private Duration eventPastTolerance = Duration.ofDays(1);
    private Duration eventFutureTolerance = Duration.ofMinutes(5);
    private int retentionDays = 90;
    private final Prometheus prometheus = new Prometheus();

    public int getMaxRangeDays() {
        return maxRangeDays;
    }

    public void setMaxRangeDays(int maxRangeDays) {
        this.maxRangeDays = maxRangeDays;
    }

    public int getTopPagesLimit() {
        return topPagesLimit;
    }

    public void setTopPagesLimit(int topPagesLimit) {
        this.topPagesLimit = topPagesLimit;
    }

    public Duration getEventPastTolerance() {
        return eventPastTolerance;
    }

    public void setEventPastTolerance(Duration eventPastTolerance) {
        this.eventPastTolerance = eventPastTolerance;
    }

    public Duration getEventFutureTolerance() {
        return eventFutureTolerance;
    }

    public void setEventFutureTolerance(Duration eventFutureTolerance) {
        this.eventFutureTolerance = eventFutureTolerance;
    }

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public static class Prometheus {
        private String baseUrl = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
