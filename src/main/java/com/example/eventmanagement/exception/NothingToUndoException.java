package com.example.eventmanagement.exception;

/** 409 — `undo` was called with an empty action stack. */
public class NothingToUndoException extends ApiException {
    public NothingToUndoException(Long eventId) {
        super(ErrorCode.NOTHING_TO_UNDO,
            "There are no pending actions left to undo on event %d.".formatted(eventId));
    }
}
