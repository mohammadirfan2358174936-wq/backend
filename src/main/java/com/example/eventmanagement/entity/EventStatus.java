package com.example.eventmanagement.entity;

/**
 * Lifecycle of an event.
 *
 * DRAFT → PUBLISHED → COMPLETED is the happy path.
 * PUBLISHED → CANCELLED is the abort path.
 * Only PUBLISHED events are publicly visible.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    COMPLETED;

    public boolean isLive() {
        return this == PUBLISHED;
    }

    public boolean isPubliclyVisible() {
        return this == PUBLISHED || this == COMPLETED;
    }
}
