package tw.bk.apppersistence.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tw.bk.appcommon.enums.AnalyticsEventType;
import tw.bk.appcommon.enums.UserRole;

@Repository
public class AnalyticsRepository {
    // 產品指標的判定條件（哪種角色算使用者、哪種事件算瀏覽）以 enum 為單一來源，
    // 不在 SQL 內散落字串字面值。
    private static final String USER_ROLE = UserRole.USER.name();
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

    public long countUsersBefore(OffsetDateTime before) {
        return queryCount("""
                SELECT count(*)
                FROM app.users
                WHERE role = :role AND created_at < :before
                """, before, null, false);
    }

    public long countNewUsers(OffsetDateTime from, OffsetDateTime to) {
        return queryCount("""
                SELECT count(*)
                FROM app.users
                WHERE role = :role
                  AND created_at >= :from
                  AND created_at < :to
                """, from, to, false);
    }

    public long countActiveUsers(OffsetDateTime from, OffsetDateTime to) {
        return queryCount("""
                SELECT count(DISTINCT e.user_id)
                FROM app.analytics_events e
                JOIN app.users u ON u.id = e.user_id
                WHERE u.role = :role
                  AND e.event_type = :eventType
                  AND e.occurred_at >= :from
                  AND e.occurred_at < :to
                """, from, to, true);
    }

    public long countPageViews(OffsetDateTime from, OffsetDateTime to) {
        return queryCount("""
                SELECT count(*)
                FROM app.analytics_events e
                JOIN app.users u ON u.id = e.user_id
                WHERE u.role = :role
                  AND e.event_type = :eventType
                  AND e.occurred_at >= :from
                  AND e.occurred_at < :to
                """, from, to, true);
    }

    public long countSessions(OffsetDateTime from, OffsetDateTime to) {
        return queryCount("""
                SELECT count(DISTINCT e.session_id)
                FROM app.analytics_events e
                JOIN app.users u ON u.id = e.user_id
                WHERE u.role = :role
                  AND e.event_type = :eventType
                  AND e.occurred_at >= :from
                  AND e.occurred_at < :to
                """, from, to, true);
    }

    public List<DailyCountRow> findRegistrationTrend(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone) {
        return jdbcClient.sql("""
                        SELECT (created_at AT TIME ZONE :timezone)::date AS day, count(*) AS value
                        FROM app.users
                        WHERE role = :role
                          AND created_at >= :from
                          AND created_at < :to
                        GROUP BY day
                        ORDER BY day
                        """)
                .param("role", USER_ROLE)
                .param("timezone", timezone)
                .param("from", from)
                .param("to", to)
                .query((rs, rowNum) -> new DailyCountRow(
                        rs.getObject("day", LocalDate.class),
                        rs.getLong("value")))
                .list();
    }

    public List<DailyEngagementRow> findEngagementTrend(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone) {
        return jdbcClient.sql("""
                        SELECT (e.occurred_at AT TIME ZONE :timezone)::date AS day,
                               count(*) AS page_views,
                               count(DISTINCT e.user_id) AS active_users
                        FROM app.analytics_events e
                        JOIN app.users u ON u.id = e.user_id
                        WHERE u.role = :role
                          AND e.event_type = :eventType
                          AND e.occurred_at >= :from
                          AND e.occurred_at < :to
                        GROUP BY day
                        ORDER BY day
                        """)
                .param("role", USER_ROLE)
                .param("eventType", PAGE_VIEW_EVENT)
                .param("timezone", timezone)
                .param("from", from)
                .param("to", to)
                .query((rs, rowNum) -> new DailyEngagementRow(
                        rs.getObject("day", LocalDate.class),
                        rs.getLong("page_views"),
                        rs.getLong("active_users")))
                .list();
    }

    public List<PageRow> findTopPages(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbcClient.sql("""
                        SELECT e.route,
                               count(*) AS views,
                               count(DISTINCT e.user_id) AS unique_users,
                               count(DISTINCT e.session_id) AS sessions
                        FROM app.analytics_events e
                        JOIN app.users u ON u.id = e.user_id
                        WHERE u.role = :role
                          AND e.event_type = :eventType
                          AND e.occurred_at >= :from
                          AND e.occurred_at < :to
                        GROUP BY e.route
                        ORDER BY views DESC, e.route
                        LIMIT :limit
                        """)
                .param("role", USER_ROLE)
                .param("eventType", PAGE_VIEW_EVENT)
                .param("from", from)
                .param("to", to)
                .param("limit", limit)
                .query((rs, rowNum) -> new PageRow(
                        rs.getString("route"),
                        rs.getLong("views"),
                        rs.getLong("unique_users"),
                        rs.getLong("sessions")))
                .list();
    }

    private long queryCount(String sql, OffsetDateTime from, OffsetDateTime to, boolean withEventType) {
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql).param("role", USER_ROLE);
        if (withEventType) {
            statement.param("eventType", PAGE_VIEW_EVENT);
        }
        if (to == null) {
            statement.param("before", from);
        } else {
            statement.param("from", from).param("to", to);
        }
        return statement.query(Long.class).single();
    }

    public record DailyCountRow(LocalDate day, long value) {
    }

    public record DailyEngagementRow(LocalDate day, long pageViews, long activeUsers) {
    }

    public record PageRow(String route, long views, long uniqueUsers, long sessions) {
    }
}
