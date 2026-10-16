package com.huiyi.assistant.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    @AfterEach
    void clear() {
        ObservabilityContextHolder.clear();
        MDC.clear();
    }

    @Test
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            String id = ObservabilityContextHolder.requestId();
            assertTrue(id.startsWith("req_"));
            assertEquals(32, id.substring(4).length());
            assertEquals(id, MDC.get("request_id"));
        };

        filter.doFilter(request, response, chain);

        String echoed = response.getHeader("X-Request-Id");
        assertTrue(echoed.startsWith("req_"));
        assertNull(ObservabilityContextHolder.requestId());
        assertNull(MDC.get("request_id"));
    }

    @Test
    void honorsIncomingRequestId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req_abc123abc123abc123abc123abc123ab");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertEquals("req_abc123abc123abc123abc123abc123ab", ObservabilityContextHolder.requestId()));

        assertEquals("req_abc123abc123abc123abc123abc123ab", response.getHeader("X-Request-Id"));
    }

    @Test
    void rejectsBlankIncomingHeaderAndGenerates() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertTrue(ObservabilityContextHolder.requestId().startsWith("req_")));

        assertFalse(response.getHeader("X-Request-Id").isBlank());
    }
}
