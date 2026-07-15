package interview.guide.infrastructure.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.slf4j.MDC.get;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesSafeCallerSuppliedRequestIdAndCleansUpMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "gateway-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, assertingFilterChain("gateway-request-123"));

        assertEquals("gateway-request-123", response.getHeader(RequestIdFilter.HEADER_NAME));
        assertNull(get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeCallerSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nheader");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, assertingFilterChain(null));

        String responseRequestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(responseRequestId);
        assertEquals(36, responseRequestId.length());
        assertNull(get(RequestIdFilter.MDC_KEY));
    }

    private FilterChain assertingFilterChain(String expectedRequestId) {
        return (request, response) -> {
            if (expectedRequestId != null) {
                assertEquals(expectedRequestId, get(RequestIdFilter.MDC_KEY));
            } else {
                assertNotNull(get(RequestIdFilter.MDC_KEY));
            }
        };
    }
}
