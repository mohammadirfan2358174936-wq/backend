package com.example.eventmanagement.exception;

/** 404 — the requested event does not exist. */
public class EventNotFoundException extends ApiException {
    public EventNotFoundException(Long id) {
        super(ErrorCode.EVENT_NOT_FOUND, "Event %d does not exist.".formatted(id));
    }

    public EventNotFoundException(String message) {
        super(ErrorCode.EVENT_NOT_FOUND, message);
    }
}
