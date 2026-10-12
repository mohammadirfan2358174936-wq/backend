package com.example.eventmanagement.exception;

/** 400 — event start/end ordering is impossible. */
public class InvalidDateRangeException extends ApiException {
    public InvalidDateRangeException() {
        super(ErrorCode.INVALID_DATE_RANGE);
    }
}
