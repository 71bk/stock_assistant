package tw.bk.appstocks.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class TpexWarrantClientTest {

    @Test
    void fetchWithRetry_retriesOnceThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = TpexWarrantClient.fetchWithRetry(() -> {
            if (calls.incrementAndGet() == 1) {
                // 模擬 TPEx 在回應途中提前關閉連線
                throw new ResourceAccessException("Connection prematurely closed DURING response");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void fetchWithRetry_returnsImmediatelyWhenFirstSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = TpexWarrantClient.fetchWithRetry(() -> {
            calls.incrementAndGet();
            return "body";
        });

        assertEquals("body", result);
        assertEquals(1, calls.get());
    }

    @Test
    void fetchWithRetry_throwsAfterAllAttemptsFail() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ResourceAccessException.class, () ->
                TpexWarrantClient.fetchWithRetry(() -> {
                    calls.incrementAndGet();
                    throw new ResourceAccessException("boom");
                }));

        assertEquals(2, calls.get());
    }
}
