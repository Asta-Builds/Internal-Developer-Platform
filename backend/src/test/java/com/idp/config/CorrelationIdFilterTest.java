package com.idp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every request carries a correlation id so log lines and problem details can be
 * joined across services.
 */
@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("propagates an inbound correlation id and exposes it on the response")
    void propagatesInboundId() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn("corr-abc123");

        org.mockito.Mockito.doAnswer(invocation -> {
            // The MDC is only populated while the chain runs; assert from inside it.
            assertThat(MDC.get("X-Correlation-ID")).isEqualTo("corr-abc123");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("X-Correlation-ID", "corr-abc123");
        assertThat(MDC.get("X-Correlation-ID")).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("generates a correlation id when the header is absent or blank")
    void generatesId() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn("   ");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(org.mockito.ArgumentMatchers.eq("X-Correlation-ID"),
                org.mockito.ArgumentMatchers.startsWith("corr-"));
    }

    @Test
    @DisplayName("cleans the MDC even when the chain throws")
    void cleansMdcOnFailure() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(chain).doFilter(request, response);

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException expected) {
            // propagate
        }

        assertThat(MDC.get("X-Correlation-ID")).isNull();
    }
}
