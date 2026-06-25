package tw.bk.appapi.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appanalytics.model.AnalyticsModels.AiUsage;
import tw.bk.appanalytics.service.AnalyticsService;
import tw.bk.appcommon.result.Result;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsControllerTest {
    @Mock
    private AnalyticsService analyticsService;

    @Test
    void aiUsage_shouldDelegateSelectedDateRange() {
        AdminAnalyticsController controller = new AdminAnalyticsController(analyticsService);
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-25");
        AiUsage expected = AiUsage.unavailable("test");
        when(analyticsService.getAiUsage(from, to, "Asia/Taipei")).thenReturn(expected);

        Result<AiUsage> result = controller.aiUsage(from, to, "Asia/Taipei");

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
        verify(analyticsService).getAiUsage(from, to, "Asia/Taipei");
    }
}
