package com.idp.config;

import com.idp.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BeanPropertyBindingResult;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * All REST errors are rendered as RFC 7807 problem details with the correlation id
 * and a timestamp attached.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/services");
        MDC.put(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-test");
    }

    private ProblemDetail extract(org.springframework.http.ResponseEntity<ProblemDetail> response) {
        return response.getBody();
    }

    @Test
    @DisplayName("renders NoSuchElementException as 404 resource-not-found")
    void notFound() {
        var response = handler.handleNotFound(new NoSuchElementException("Service not found: srv-1"), request);
        ProblemDetail problem = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problem.getDetail()).isEqualTo("Service not found: srv-1");
        assertThat(problem.getType().toString()).endsWith("/resource-not-found");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/services");
        assertThat(problem.getProperties()).containsKey("correlationId");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("renders IllegalArgumentException as 400 bad-request")
    void badRequest() {
        var response = handler.handleBadRequest(new IllegalArgumentException("nope"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Request Parameters");
        assertThat(response.getBody().getType().toString()).endsWith("/bad-request");
    }

    @Test
    @DisplayName("renders validation failures as 422 with per-field errors")
    void validationFailure() {
        var target = new Payload();
        var binding = new BeanPropertyBindingResult(target, "payload");
        binding.rejectValue("name", "NotBlank", "must not be blank");
        var exception = new MethodArgumentNotValidException(null, binding);

        var response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> fieldErrors = (java.util.Map<String, String>)
                response.getBody().getProperties().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("name", "must not be blank");
    }

    /** A bindable payload exposing a {@code name} property. */
    private static final class Payload {
        private String name;

        @SuppressWarnings("unused")
        public String getName() {
            return name;
        }

        @SuppressWarnings("unused")
        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    @DisplayName("renders method-security refusals as 403 access-denied")
    void accessDenied() {
        var response = handler.handleAccessDenied(new AccessDeniedException("no"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getTitle()).isEqualTo("Access Denied");
        assertThat(response.getBody().getType().toString()).endsWith("/access-denied");
    }

    @Test
    @DisplayName("renders authentication failures as 401 unauthenticated")
    void unauthenticated() {
        var response = handler.handleAuthentication(new AuthenticationException("no") {}, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getTitle()).isEqualTo("Authentication Required");
        assertThat(response.getBody().getType().toString()).endsWith("/unauthenticated");
    }

    @Test
    @DisplayName("renders unknown exceptions as 500 internal-server-error")
    void genericFailure() {
        var response = handler.handleGenericException(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("boom");
        assertThat(response.getBody().getType().toString()).endsWith("/internal-server-error");
    }
}
