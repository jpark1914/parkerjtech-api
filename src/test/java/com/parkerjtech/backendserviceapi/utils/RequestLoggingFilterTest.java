package com.parkerjtech.backendserviceapi.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {
    @Test void rethrowsFailureSoNormalServerErrorHandlingStillWorks() {
        var actions = new CrmActionLogger(Clock.fixed(Instant.parse("2026-11-13T18:00:00Z"), ZoneOffset.UTC), "America/Chicago");
        var filter = new RequestLoggingFilter(actions, false);
        var request = new MockHttpServletRequest("GET", "/api/demo/leads");
        var failure = new ServletException("forced filter failure");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw failure; })).isSameAs(failure);
    }
}
