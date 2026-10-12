package com.example.eventmanagement.exception;

import lombok.Getter;

/**
 * Aggregates Bean Validation failures into one response body.
 *
 * Produced by `GlobalExceptionHandler` from `MethodArgumentNotValidException`/
 * `ConstraintViolationException` and carrying a `Map<field, message>` so the
 * form wizard can light up each individual field.
 */
@Getter
public class ValidationException extends ApiException {

    private final java.util.Map<String, String> fieldErrors;

    public ValidationException(java.util.Map<String, String> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR);
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String message, java.util.Map<String, String> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = fieldErrors;
    }
}
