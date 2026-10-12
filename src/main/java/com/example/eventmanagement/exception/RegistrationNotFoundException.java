package com.example.eventmanagement.exception;

/** 404 — the caller has no booking for this event. */
public class RegistrationNotFoundException extends ApiException {
    public RegistrationNotFoundException(Long eventId, Long userId) {
        super(ErrorCode.REGISTRATION_NOT_FOUND,
            "No registration found for user %d on event %d.".formatted(userId, eventId));
    }
}
