package com.idp.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Standard RFC 7807 Problem Details Global Exception Handler.
 * Formats all REST errors according to RFC 7807 with type, title, status, detail, instance,
 * correlationId, and timestamp.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE_URL = "https://idp.enterprise.internal/errors/";

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URL + "resource-not-found"));
        problem.setTitle("Resource Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblem(problem);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URL + "bad-request"));
        problem.setTitle("Invalid Request Parameters");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblem(problem);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed for one or more fields");
        problem.setType(URI.create(PROBLEM_BASE_URL + "validation-error"));
        problem.setTitle("Schema Validation Error");
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> 
            fieldErrors.put(err.getField(), err.getDefaultMessage())
        );
        problem.setProperty("fieldErrors", fieldErrors);
        enrichProblem(problem);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /**
     * Method-security refusals surface as {@link AccessDeniedException} thrown inside
     * the controller invocation, so this advice sees them before Spring Security's
     * {@code ExceptionTranslationFilter} would. Without an explicit handler the
     * catch-all below would render an authorization refusal as a 500.
     *
     * <p>The body stays deliberately vague about which rule refused; the full reason
     * is already in the audit log.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "The platform authorization policy does not permit this action. "
                        + "The decision has been recorded in the audit log.");
        problem.setType(URI.create(PROBLEM_BASE_URL + "access-denied"));
        problem.setTitle("Access Denied");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblem(problem);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "A valid bearer token issued by the platform identity provider is required.");
        problem.setType(URI.create(PROBLEM_BASE_URL + "unauthenticated"));
        problem.setTitle("Authentication Required");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblem(problem);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "An unexpected enterprise error occurred"
        );
        problem.setType(URI.create(PROBLEM_BASE_URL + "internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblem(problem);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private void enrichProblem(ProblemDetail problem) {
        String corrId = MDC.get(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (corrId != null) {
            problem.setProperty("correlationId", corrId);
        }
        problem.setProperty("timestamp", Instant.now().toString());
    }
}
