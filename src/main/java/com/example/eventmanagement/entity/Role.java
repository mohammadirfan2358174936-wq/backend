package com.example.eventmanagement.entity;

/** Platform roles. Stored as a string so adding a role never rewrites rows. */
public enum Role {
    ADMIN,
    ORGANIZER,
    ATTENDEE;

    /** Spring Security authorities are prefixed `ROLE_`. */
    public String authority() {
        return "ROLE_" + name();
    }

    public boolean canManageEvents() {
        return this == ADMIN || this == ORGANIZER;
    }
}
