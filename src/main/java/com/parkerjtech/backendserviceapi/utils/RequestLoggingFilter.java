package com.parkerjtech.backendserviceapi.utils;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final CrmActionLogger actions;
    private final boolean stackTraces;

    public RequestLoggingFilter(CrmActionLogger actions,
            @Value("${crm.logging.exception-stack-traces:false}") boolean stackTraces) {
        this.actions = actions;
        this.stackTraces = stackTraces;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/demo/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        boolean failed = false;
        String method = CrmActionLogger.singleLine(request.getMethod());
        String path = CrmActionLogger.singleLine(request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failed = true;
            LOG.error("Request processing failed localTime={} method={} path={} exceptionType={}",
                    actions.localTime(), method, path, ex.getClass().getName());
            if (stackTraces) LOG.error("Request processing exception", ex);
            throw ex;
        } finally {
            int status = failed ? 500 : response.getStatus();
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            var level = status >= 500 ? org.slf4j.event.Level.ERROR
                    : status >= 400 ? org.slf4j.event.Level.WARN : org.slf4j.event.Level.INFO;
            LOG.atLevel(level).log("Request completed localTime={} method={} path={} status={} durationMs={}",
                    actions.localTime(), method, path, status, durationMs);
        }
    }
}
