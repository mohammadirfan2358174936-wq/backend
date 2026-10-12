package com.example.eventmanagement.exception;

/** 403 — the caller is authenticated but is not the event's organiser. */
public class NotEventOwnerException extends ApiException {
    public NotEventOwnerException() {
        super(ErrorCode.NOT_EVENT_OWNER);
    }
}
