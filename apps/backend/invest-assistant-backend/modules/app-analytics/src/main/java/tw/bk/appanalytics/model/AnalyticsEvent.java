package tw.bk.appanalytics.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalyticsEvent(
        UUID eventId,
        UUID sessionId,
        String eventType,
        String route,
        OffsetDateTime occurredAt) {
}
