package tw.bk.appanalytics.port;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface AnalyticsQueryPort {
    long countUsersBefore(OffsetDateTime before);

    long countNewUsers(OffsetDateTime from, OffsetDateTime to);

    long countActiveUsers(OffsetDateTime from, OffsetDateTime to);

    long countPageViews(OffsetDateTime from, OffsetDateTime to);

    long countSessions(OffsetDateTime from, OffsetDateTime to);

    List<DailyCountRow> findRegistrationTrend(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone);

    List<DailyEngagementRow> findEngagementTrend(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone);

    List<PageRow> findTopPages(OffsetDateTime from, OffsetDateTime to, int limit);

    record DailyCountRow(LocalDate day, long value) {
    }

    record DailyEngagementRow(LocalDate day, long pageViews, long activeUsers) {
    }

    record PageRow(String route, long views, long uniqueUsers, long sessions) {
    }
}
