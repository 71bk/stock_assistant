package tw.bk.appanalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appanalytics.config.AnalyticsProperties;
import tw.bk.appanalytics.model.AnalyticsEvent;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsage;
import tw.bk.appanalytics.model.AnalyticsModels.DailyTrend;
import tw.bk.appanalytics.model.AnalyticsModels.PageMetric;
import tw.bk.appanalytics.model.AnalyticsModels.Summary;
import tw.bk.appanalytics.port.AnalyticsQueryPort;
import tw.bk.appanalytics.port.AnalyticsQueryPort.DailyCountRow;
import tw.bk.appanalytics.port.AnalyticsQueryPort.DailyEngagementRow;
import tw.bk.appanalytics.port.AnalyticsQueryPort.PageRow;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.repository.AnalyticsRepository;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private AnalyticsRepository repository;

    @Mock
    private AnalyticsQueryPort queryPort;

    @Mock
    private PrometheusAnalyticsClient prometheusClient;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(
                repository,
                queryPort,
                prometheusClient,
                new AnalyticsProperties(),
                CLOCK);
    }

    @Test
    void getSummary_shouldUseAnalyticsQueryPort() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-17T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-25T00:00:00+08:00");
        OffsetDateTime now = OffsetDateTime.parse("2026-06-24T12:00:00Z");

        when(queryPort.countUsersBefore(to)).thenReturn(100L);
        when(queryPort.countNewUsers(from, to)).thenReturn(8L);
        when(queryPort.countActiveUsers(now.minusDays(1), now)).thenReturn(4L);
        when(queryPort.countActiveUsers(now.minusDays(7), now)).thenReturn(20L);
        when(queryPort.countActiveUsers(now.minusDays(30), now)).thenReturn(55L);
        when(queryPort.countPageViews(from, to)).thenReturn(300L);
        when(queryPort.countSessions(from, to)).thenReturn(80L);

        Summary result = service.getSummary(
                LocalDate.parse("2026-06-17"),
                LocalDate.parse("2026-06-24"),
                "Asia/Taipei");

        assertEquals(100L, result.totalUsers());
        assertEquals(8L, result.newUsers());
        assertEquals(4L, result.dau());
        assertEquals(20L, result.wau());
        assertEquals(55L, result.mau());
        assertEquals(300L, result.pageViews());
        assertEquals(80L, result.sessions());
        verifyNoInteractions(repository);
    }

    @Test
    void getDailyTrend_shouldMergeQueryPortRowsAndFillMissingDays() {
        LocalDate firstDay = LocalDate.parse("2026-06-17");
        LocalDate secondDay = firstDay.plusDays(1);
        OffsetDateTime from = OffsetDateTime.parse("2026-06-17T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-19T00:00:00+08:00");
        when(queryPort.findRegistrationTrend(from, to, "Asia/Taipei"))
                .thenReturn(List.of(new DailyCountRow(firstDay, 3L)));
        when(queryPort.findEngagementTrend(from, to, "Asia/Taipei"))
                .thenReturn(List.of(new DailyEngagementRow(secondDay, 9L, 4L)));

        List<DailyTrend> result = service.getDailyTrend(firstDay, secondDay, "Asia/Taipei");

        assertEquals(List.of(
                new DailyTrend(firstDay, 3L, 0L, 0L),
                new DailyTrend(secondDay, 0L, 4L, 9L)), result);
    }

    @Test
    void getTopPages_shouldMapQueryPortRows() {
        LocalDate fromDate = LocalDate.parse("2026-06-17");
        LocalDate toDate = LocalDate.parse("2026-06-24");
        OffsetDateTime from = OffsetDateTime.parse("2026-06-17T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-25T00:00:00+08:00");
        when(queryPort.findTopPages(from, to, 10))
                .thenReturn(List.of(new PageRow("/portfolio", 30L, 12L, 15L)));

        List<PageMetric> result = service.getTopPages(fromDate, toDate, "Asia/Taipei");

        assertEquals(List.of(new PageMetric("/portfolio", 30L, 12L, 15L)), result);
    }

    @Test
    void recordEvents_shouldNormalizeRouteAndInsertIdempotentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-06-24T11:59:00Z");
        when(repository.insertPageView(7L, eventId, sessionId, "/portfolio", occurredAt))
                .thenReturn(1);

        int inserted = service.recordEvents(7L, List.of(new AnalyticsEvent(
                eventId,
                sessionId,
                "PAGE_VIEW",
                "/portfolio?tab=positions#top",
                occurredAt)));

        assertEquals(1, inserted);
        verify(repository).insertPageView(7L, eventId, sessionId, "/portfolio", occurredAt);
    }

    @Test
    void recordEvents_shouldRejectTimestampOutsideTolerance() {
        AnalyticsEvent event = new AnalyticsEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PAGE_VIEW",
                "/dashboard",
                OffsetDateTime.parse("2026-06-20T12:00:00Z"));

        assertThrows(BusinessException.class, () -> service.recordEvents(7L, List.of(event)));
    }

    @Test
    void getSummary_shouldRejectRangesLongerThanNinetyDays() {
        assertThrows(BusinessException.class, () -> service.getSummary(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-06-24"),
                "Asia/Taipei"));
    }

    @Test
    void getAiUsage_shouldQueryPrometheusForSelectedRange() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-17T00:00:00+08:00");
        OffsetDateTime now = OffsetDateTime.parse("2026-06-24T12:00:00Z");
        AiUsage expected = AiUsage.unavailable("test");
        when(prometheusClient.queryAiUsage(from, now)).thenReturn(expected);

        AiUsage result = service.getAiUsage(
                LocalDate.parse("2026-06-17"),
                LocalDate.parse("2026-06-24"),
                "Asia/Taipei");

        assertEquals(expected, result);
        verify(prometheusClient).queryAiUsage(from, now);
    }
}
