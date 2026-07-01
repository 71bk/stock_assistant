package tw.bk.apppersistence.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tw.bk.appcommon.enums.AnalyticsEventType;

@Repository
public class AnalyticsRepository {
    private static final String PAGE_VIEW_EVENT = AnalyticsEventType.PAGE_VIEW.name();

    private final JdbcClient jdbcClient;

    public AnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int insertPageView(
            Long userId,
            UUID eventId,
            UUID sessionId,
            String route,
            OffsetDateTime occurredAt) {
        return jdbcClient.sql("""
                        INSERT INTO app.analytics_events (
                            event_id, user_id, session_id, event_type, route, occurred_at
                        )
                        VALUES (:eventId, :userId, :sessionId, :eventType, :route, :occurredAt)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("userId", userId)
                .param("sessionId", sessionId)
                .param("eventType", PAGE_VIEW_EVENT)
                .param("route", route)
                .param("occurredAt", occurredAt)
                .update();
    }

    public int deleteEventsBefore(OffsetDateTime before) {
        return jdbcClient.sql("""
                        DELETE FROM app.analytics_events
                        WHERE occurred_at < :before
                        """)
                .param("before", before)
                .update();
    }
}
