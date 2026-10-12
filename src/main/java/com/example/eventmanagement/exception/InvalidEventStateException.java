package com.example.eventmanagement.exception;

/** 409 — the event's status forbids this transition. */
public class InvalidEventStateException extends ApiException {
    public InvalidEventStateException(String message) {
        super(ErrorCode.INVALID_EVENT_STATE, message);
    }

    public InvalidEventStateException(com.example.eventmanagement.entity.EventStatus from,
                                      com.example.eventmanagement.entity.EventStatus to) {
        super(ErrorCode.INVALID_EVENT_STATE,
            "Cannot change event status from %s to %s.".formatted(from, to));
    }
}
