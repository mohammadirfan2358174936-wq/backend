package com.example.eventmanagement.exception;

/** 404 — the requested ticket does not exist. */
public class TicketNotFoundException extends ApiException {
    public TicketNotFoundException(String ticketNumber) {
        super(ErrorCode.TICKET_NOT_FOUND, "Ticket “%s” does not exist.".formatted(ticketNumber));
    }
}
