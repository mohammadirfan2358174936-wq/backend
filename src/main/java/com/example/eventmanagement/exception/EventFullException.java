package com.example.eventmanagement.exception;

/** 409 — no seats remain for the requested tier. */
public class EventFullException extends ApiException {
    public EventFullException(String eventTitle, int available, int requested) {
        super(ErrorCode.EVENT_FULL,
            "“%s” only has %d seat(s) available but %d were requested."
                .formatted(eventTitle, Math.max(available, 0), requested));
    }

    public EventFullException(String tierName) {
        super(ErrorCode.EVENT_FULL, "The “%s” tier is sold out.".formatted(tierName));
    }
}
