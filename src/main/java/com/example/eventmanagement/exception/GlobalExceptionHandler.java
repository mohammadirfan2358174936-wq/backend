package com.example.eventmanagement.exception;

import com.example.eventmanagement.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GLOBAL EXCEPTION HANDLER
 * ========================
 * One place where every failure becomes the documented JSON envelope.
 *
 * Guarantees:
 *  - `ApiException` → its own `ErrorCode`, HTTP status and message.
 *  - Bean Validation → 400 with a `field → message` map.
 *  - Optimistic locking / unique-key races → 409 CONFLICT (never a 500), which
 *    is exactly what the frontend needs to show "this record changed, reload".
 *  - Everything unknown → 500 with a correlation id logged, message redacted.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /* ------------------------------------------------------------------- 4xx */

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleApi(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();

        if (ex instanceof ValidationException validation) {
            return build(code, ex.getMessage(), validation.getFieldErrors(), request);
        }

        // 5xx here means a real bug; surface it loudly.
        if (code.getStatus().is5xxServerError()) {
            log.error("Unhandled domain exception [{}] at {}", code.name(), request.getRequestURI(), ex);
        } else {
            log.debug("Domain exception [{}] at {}: {}", code.name(), request.getRequestURI(), ex.getMessage());
        }

        return build(code, ex.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        });

        log.debug("Validation failed at {}: {}", request.getRequestURI(), errors);
        return build(ErrorCode.VALIDATION_ERROR, "Please correct the highlighted fields.", errors, request);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleConstraint(
        jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {

        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                v -> v.getMessage(),
                (a, b) -> a,
                LinkedHashMap::new));

        return build(ErrorCode.VALIDATION_ERROR, "Please correct the highlighted fields.", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleUnreadable(
        HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body at {}", request.getRequestURI());
        return build(ErrorCode.BAD_REQUEST, "The request body is missing or malformed.", null, request);
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleBadParams(
        Exception ex, HttpServletRequest request) {
        return build(ErrorCode.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleDenied(
        AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.FORBIDDEN, null, null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleAuth(
        AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHORIZED, "Authentication is required.", null, request);
    }

    /* ------------------------------------------------------- concurrency 409 */

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleOptimistic(
        OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock failure at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.CONCURRENT_MODIFICATION, null, null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleIntegrity(
        DataIntegrityViolationException ex, HttpServletRequest request) {

        String root = rootMessage(ex);

        // A lost race on UNIQUE(user_id, event_id) — two simultaneous bookings.
        if (root != null && root.contains("uk_registrations_user_event")) {
            log.info("Duplicate registration race at {}: {}", request.getRequestURI(), root);
            return build(ErrorCode.DUPLICATE_REGISTRATION,
                "You have already registered for this event.", null, request);
        }

        if (root != null && (root.contains("uk_users_email") || root.toLowerCase().contains("email"))) {
            return build(ErrorCode.EMAIL_ALREADY_EXISTS, null, null, request);
        }

        if (root != null && root.contains("uk_events_slug")) {
            return build(ErrorCode.SLUG_ALREADY_EXISTS, null, null, request);
        }

        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), root);
        return build(ErrorCode.CONFLICT,
            "This change violates a database constraint.", null, request);
    }

    /* ------------------------------------------------------------------ 500 */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse.ApiBody<Void>> handleUnexpected(
        Exception ex, HttpServletRequest request) {

        // Correlation id travels to the client so support can find the log line.
        String correlation = Integer.toHexString(java.util.Objects.hash(ex.getMessage(), System.nanoTime()));
        log.error("Unhandled exception [{}] at {}: {}",
            correlation, request.getRequestURI(), ex.getMessage(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong on our side. Please try again. (ref %s)".formatted(correlation),
            ErrorCode.INTERNAL_ERROR.name(), null, request);
    }

    /* --------------------------------------------------------------- helpers */

    private ResponseEntity<ApiResponse.ApiBody<Void>> build(
        ErrorCode code, String message, Map<String, String> errors, HttpServletRequest request) {

        String body = message != null ? message : code.getMessage();

        if (errors != null && !errors.isEmpty()) {
            return ResponseEntity.status(code.getStatus())
                .header("X-Error-Code", code.name())
                .body(ApiResponse.failure(body, code.name(), errors));
        }

        return ResponseEntity.status(code.getStatus())
            .header("X-Error-Code", code.name())
            .body(ApiResponse.failure(body, code.name()));
    }

    private ResponseEntity<ApiResponse.ApiBody<Void>> build(
        HttpStatus status, String message, String errorCode, Map<String, String> errors,
        HttpServletRequest request) {

        return ResponseEntity.status(status)
            .header("X-Error-Code", errorCode)
            .body(errors == null || errors.isEmpty()
                ? ApiResponse.failure(message, errorCode)
                : ApiResponse.failure(message, errorCode, errors));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    /** Kept for symmetry with a `WebRequest` overload some services rely on. */
    @SuppressWarnings("unused")
    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest sw ? sw.getRequest().getRequestURI() : "unknown";
    }
}
