package com.example.eventmanagement.entity;

/** Ticket lifecycle. `USED` is written by the organiser's check-in scan. */
public enum TicketStatus {
    VALID,
    USED,
    REFUNDED,
    EXPIRED;

    public boolean isRedeemable() {
        return this == VALID;
    }
}
