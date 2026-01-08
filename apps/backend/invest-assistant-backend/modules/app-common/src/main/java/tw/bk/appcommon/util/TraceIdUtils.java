package tw.bk.appcommon.util;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceIdUtils {
    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtils() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
