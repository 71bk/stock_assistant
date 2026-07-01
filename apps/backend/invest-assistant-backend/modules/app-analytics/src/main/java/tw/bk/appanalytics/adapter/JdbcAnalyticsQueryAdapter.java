package tw.bk.appanalytics.adapter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tw.bk.appanalytics.port.AnalyticsQueryPort;
import tw.bk.appcommon.enums.AnalyticsEventType;
import tw.bk.appcommon.enums.UserRole;

@Repository
public class JdbcAnalyticsQueryAdapter implements AnalyticsQueryPort {
    private static final String USER_ROLE = UserRole.USER.name();
    private static final String PAGE_VIEW_EVENT = AnalyticsEventType.PAGE_VIEW.name();

    private final JdbcClient jdbcClient;

    public JdbcAnalyticsQueryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public long countUsersBefore(OffsetDateTime before) {
        return queryCount("""
                SELECT count(*)
                FROM app.users
                WHERE role = :role AND created_at < :before
                """, before, null, false);
    }

    @Override
    public long countNewUsers(OffsetDateTime from, OffsetDateTime to) {
        return queryCount("""
                SELECT count(*)
                FROM app.users
                WHERE role = :role
                  AND created_at >= :from
                  AND created_at < :to
                """, from, to, false);
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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
}
