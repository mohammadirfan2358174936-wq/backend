package com.example.eventmanagement.exception;

/** 409 — the user already holds a booking for this event. */
public class DuplicateRegistrationException extends ApiException {
    public DuplicateRegistrationException(String eventTitle) {
        super(ErrorCode.DUPLICATE_REGISTRATION,
            "You have already registered for “%s”.".formatted(eventTitle));
    }
}
