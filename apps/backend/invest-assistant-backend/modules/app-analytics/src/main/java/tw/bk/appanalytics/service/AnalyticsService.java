package tw.bk.appanalytics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appanalytics.config.AnalyticsProperties;
import tw.bk.appanalytics.model.AnalyticsEvent;
import tw.bk.appanalytics.model.AnalyticsModels.ApiTraffic;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsage;
import tw.bk.appanalytics.model.AnalyticsModels.DailyTrend;
import tw.bk.appanalytics.model.AnalyticsModels.PageMetric;
import tw.bk.appanalytics.model.AnalyticsModels.Summary;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.repository.AnalyticsRepository;
import tw.bk.apppersistence.repository.AnalyticsRepository.DailyCountRow;
import tw.bk.apppersistence.repository.AnalyticsRepository.DailyEngagementRow;

@Service
public class AnalyticsService {
    private static final String PAGE_VIEW = "PAGE_VIEW";

    private final AnalyticsRepository repository;
    private final PrometheusAnalyticsClient prometheusClient;
    private final AnalyticsProperties properties;
    private final Clock clock;

    @Autowired
    public AnalyticsService(
            AnalyticsRepository repository,
            PrometheusAnalyticsClient prometheusClient,
            AnalyticsProperties properties) {
        this(repository, prometheusClient, properties, Clock.systemUTC());
    }

    AnalyticsService(
            AnalyticsRepository repository,
            PrometheusAnalyticsClient prometheusClient,
            AnalyticsProperties properties,
            Clock clock) {
        this.repository = repository;
        this.prometheusClient = prometheusClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int recordEvents(Long userId, List<AnalyticsEvent> events) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        int inserted = 0;
        for (AnalyticsEvent event : events) {
            validateEvent(event, now);
            inserted += repository.insertPageView(
                    userId,
                    event.eventId(),
                    event.sessionId(),
                    normalizeRoute(event.route()),
                    event.occurredAt());
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public Summary getSummary(LocalDate from, LocalDate to, String timezone) {
        DateRange range = dateRange(from, to, timezone);
        OffsetDateTime activityEnd = min(range.toExclusive(), OffsetDateTime.now(clock));
        return new Summary(
                repository.countUsersBefore(range.toExclusive()),
                repository.countNewUsers(range.fromInclusive(), range.toExclusive()),
                repository.countActiveUsers(activityEnd.minusDays(1), activityEnd),
                repository.countActiveUsers(activityEnd.minusDays(7), activityEnd),
                repository.countActiveUsers(activityEnd.minusDays(30), activityEnd),
                repository.countPageViews(range.fromInclusive(), range.toExclusive()),
                repository.countSessions(range.fromInclusive(), range.toExclusive()));
    }

    @Transactional(readOnly = true)
    public List<DailyTrend> getDailyTrend(LocalDate from, LocalDate to, String timezone) {
        DateRange range = dateRange(from, to, timezone);
        Map<LocalDate, Long> registrations = new HashMap<>();
        for (DailyCountRow row : repository.findRegistrationTrend(
                range.fromInclusive(), range.toExclusive(), range.timezone().getId())) {
            registrations.put(row.day(), row.value());
        }

        Map<LocalDate, DailyEngagementRow> engagement = new HashMap<>();
        for (DailyEngagementRow row : repository.findEngagementTrend(
                range.fromInclusive(), range.toExclusive(), range.timezone().getId())) {
            engagement.put(row.day(), row);
        }

        List<DailyTrend> result = new ArrayList<>();
        for (LocalDate day = range.fromDate(); !day.isAfter(range.toDate()); day = day.plusDays(1)) {
            DailyEngagementRow row = engagement.get(day);
            result.add(new DailyTrend(
                    day,
                    registrations.getOrDefault(day, 0L),
                    row == null ? 0 : row.activeUsers(),
                    row == null ? 0 : row.pageViews()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PageMetric> getTopPages(LocalDate from, LocalDate to, String timezone) {
        DateRange range = dateRange(from, to, timezone);
        return repository.findTopPages(
                        range.fromInclusive(),
                        range.toExclusive(),
                        properties.getTopPagesLimit())
                .stream()
                .map(row -> new PageMetric(row.route(), row.views(), row.uniqueUsers(), row.sessions()))
                .toList();
    }

    public ApiTraffic getApiTraffic(LocalDate from, LocalDate to, String timezone) {
        DateRange range = dateRange(from, to, timezone);
        OffsetDateTime queryEnd = min(range.toExclusive(), OffsetDateTime.now(clock));
        return prometheusClient.queryTraffic(range.fromInclusive(), queryEnd);
    }

    public AiUsage getAiUsage(LocalDate from, LocalDate to, String timezone) {
        DateRange range = dateRange(from, to, timezone);
        OffsetDateTime queryEnd = min(range.toExclusive(), OffsetDateTime.now(clock));
        return prometheusClient.queryAiUsage(range.fromInclusive(), queryEnd);
    }

    private void validateEvent(AnalyticsEvent event, OffsetDateTime now) {
        if (event == null
                || event.eventId() == null
                || event.sessionId() == null
                || event.occurredAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Analytics event fields are required");
        }
        if (!PAGE_VIEW.equals(event.eventType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported analytics event type");
        }
        if (event.route() == null || event.route().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Analytics route is required");
        }
        OffsetDateTime earliest = now.minus(properties.getEventPastTolerance());
        OffsetDateTime latest = now.plus(properties.getEventFutureTolerance());
        if (event.occurredAt().isBefore(earliest) || event.occurredAt().isAfter(latest)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Analytics event timestamp is outside allowed range");
        }
        String route = normalizeRoute(event.route());
        if (!route.startsWith("/") || route.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid analytics route");
        }
    }

    private String normalizeRoute(String rawRoute) {
        String route = rawRoute.trim();
        int queryIndex = route.indexOf('?');
        int hashIndex = route.indexOf('#');
        int endIndex = route.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (hashIndex >= 0) {
            endIndex = Math.min(endIndex, hashIndex);
        }
        return route.substring(0, endIndex);
    }

    private DateRange dateRange(LocalDate from, LocalDate to, String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Taipei" : timezone);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid timezone");
        }

        LocalDate effectiveTo = to == null ? LocalDate.now(clock.withZone(zoneId)) : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(6) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from must not be after to");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;
        if (days > properties.getMaxRangeDays()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Analytics date range exceeds " + properties.getMaxRangeDays() + " days");
        }

        return new DateRange(
                effectiveFrom,
                effectiveTo,
                effectiveFrom.atStartOfDay(zoneId).toOffsetDateTime(),
                effectiveTo.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime(),
                zoneId);
    }

    private OffsetDateTime min(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private record DateRange(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive,
            ZoneId timezone) {
    }
}
