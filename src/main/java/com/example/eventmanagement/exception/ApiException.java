package com.example.eventmanagement.exception;

/**
 * Base class for every domain exception.
 *
 * Deliberately unchecked: a `DataAccessException` or a violated unique
 * constraint must be able to roll the transaction back without every service
 * method declaring `throws`. The `GlobalExceptionHandler` maps these to the
 * JSON envelope, so nothing leaks as a stack trace.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** The wire format for this exception's code, e.g. `EVENT_FULL`. */
    public String code() {
        return toWireCode(errorCode);
    }

    /** `EVENT_FULL` from `EVENT_NOT_FOUND` → `EVENT_NOT_FOUND`. */
    public static String toWireCode(ErrorCode code) {
        return code.name();
    }
}
