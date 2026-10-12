package com.example.eventmanagement.entity;

/**
 * The vocabulary of the organiser action stack (see `EventActionStack`).
 *
 * Every mutating operation on an event pushes one of these. `UNDO` reverses the
 * newest entry — each constant below knows its own inverse through
 * `EventActionType#inverse()`.
 */
public enum EventActionType {
    CREATE_EVENT("created the event"),
    UPDATE_EVENT("updated the event details"),
    CHANGE_CAPACITY("changed the event capacity"),
    PUBLISH_EVENT("published the event"),
    CANCEL_EVENT("cancelled the event"),
    DELETE_EVENT("deleted the event"),
    ADD_TICKET("added a ticket tier"),
    UPDATE_TICKET("updated a ticket tier");

    private final String description;

    EventActionType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * Which stored entry the type came from, so `undo` knows what to restore.
     * `CREATE_EVENT` has no inverse — it is undone by deleting the event.
     */
    public boolean restoresSnapshot() {
        return this == UPDATE_EVENT || this == CHANGE_CAPACITY;
    }

    /** True when the operation can be reversed. */
    public boolean isReversible() {
        return true;
    }
}
