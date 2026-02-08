package tw.bk.appcommon.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tw.bk.appcommon.util.TraceIdUtils;

/**
 * Ensure traceId exists for every request and is propagated via header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            TraceIdUtils.setTraceId(incoming.trim());
        } else {
            TraceIdUtils.getOrCreateTraceId();
        }

        response.setHeader(TRACE_ID_HEADER, TraceIdUtils.getTraceId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdUtils.clear();
        }
    }
}
