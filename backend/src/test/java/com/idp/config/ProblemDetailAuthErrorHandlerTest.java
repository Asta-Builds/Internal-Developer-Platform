package com.idp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Security-filter-level 401/403 responses are problem details, matching the format
 * the application exception handler produces.
 */
@ExtendWith(MockitoExtension.class)
class ProblemDetailAuthErrorHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private final ProblemDetailAuthErrorHandler handler =
            new ProblemDetailAuthErrorHandler(new ObjectMapper());

    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/catalog/services");
        output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) {
                output.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            }
        });
        MDC.put(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-auth");
    }

    @Test
    @DisplayName("commence writes a 401 problem detail")
    void commenceWrites401() throws Exception {
        handler.commence(request, response, new AuthenticationException("no token") {});

        String body = output.toString("UTF-8");
        assertThat(body).contains("\"status\":401").contains("\"title\":\"Authentication Required\"");
    }

    @Test
    @DisplayName("handle writes a 403 problem detail")
    void handleWrites403() throws Exception {
        handler.handle(request, response, new AccessDeniedException("no"));

        String body = output.toString("UTF-8");
        assertThat(body).contains("\"status\":403").contains("\"title\":\"Access Denied\"");
    }
}
