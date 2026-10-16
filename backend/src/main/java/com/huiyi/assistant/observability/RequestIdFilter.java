package com.huiyi.assistant.observability;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String RUN_ID_HEADER = "X-Run-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = ObservabilityIds.isValidRequestId(incoming)
                ? incoming.trim()
                : ObservabilityIds.requestId();
        ObservabilityContextHolder.setRequestId(requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
            String runId = ObservabilityContextHolder.runId();
            if (runId != null && !response.isCommitted()) {
                response.setHeader(RUN_ID_HEADER, runId);
            }
        } finally {
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) {
                        ObservabilityContextHolder.clear();
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                        ObservabilityContextHolder.clear();
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                        ObservabilityContextHolder.clear();
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                    }
                });
            } else {
                ObservabilityContextHolder.clear();
            }
        }
    }
}
