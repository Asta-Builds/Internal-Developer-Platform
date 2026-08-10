package com.idp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * Renders 401 and 403 responses as RFC 7807 problem details, matching the format
 * {@link GlobalExceptionHandler} produces for application errors.
 *
 * <p>Deliberately terse: the body states that access was refused without disclosing
 * which policy refused it. The full reason goes to the audit log instead.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String PROBLEM_BASE_URL = "https://idp.enterprise.internal/errors/";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication Required",
                "A valid bearer token issued by the platform identity provider is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "access-denied", "Access Denied",
                "The platform authorization policy does not permit this action. "
                        + "The decision has been recorded in the audit log.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String typeSlug, String title, String detail) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE_URL + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));

        String corrId = MDC.get(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (corrId != null) {
            problem.setProperty("correlationId", corrId);
        }
        problem.setProperty("timestamp", Instant.now().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
