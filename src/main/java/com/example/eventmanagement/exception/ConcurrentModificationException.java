package com.example.eventmanagement.exception;

/** 409 — optimistic lock lost, or the action stack is empty. */
public class ConcurrentModificationException extends ApiException {
    public ConcurrentModificationException(String message) {
        super(ErrorCode.CONCURRENT_MODIFICATION, message);
    }

    public ConcurrentModificationException(Long id) {
        super(ErrorCode.CONCURRENT_MODIFICATION,
            "Event %d was modified by someone else. Please reload and try again.".formatted(id));
    }
}
