package com.example.eventmanagement.entity;

/** Booking state for a registration. */
public enum RegistrationStatus {
    CONFIRMED,
    PENDING,
    CANCELLED,
    REFUNDED;

    public boolean occupiesSeat() {
        return this == CONFIRMED || this == PENDING;
    }
}
