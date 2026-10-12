package com.example.eventmanagement.service;

import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventActionType;
import com.example.eventmanagement.entity.User;

import java.util.Map;

/**
 * Immutable command describing one organiser action.
 *
 * The service layer builds one of these and hands it to
 * `EventActionService#record`. Making it a record means an action can never be
 * persisted without its snapshot, and keeps the write order obvious at the call
 * site: snapshot taken → command built → business mutation → command recorded.
 *
 * @param actor         who performed it
 * @param type          what it was
 * @param event         the aggregate it applies to
 * @param previousState JSON of the event *before* the mutation
 * @param snapshotAfter JSON of the event *after* the mutation
 * @param extra         arbitrary detail (tier name, seat change, ...)
 */
public record EventCommand(
    User actor,
    EventActionType type,
    Event event,
    Map<String, Object> previousState,
    Map<String, Object> snapshotAfter,
    Map<String, Object> extra
) {

    public static EventCommand of(
        User actor, EventActionType type, Event event,
        Map<String, Object> previousState, Map<String, Object> snapshotAfter) {
        return new EventCommand(actor, type, event, previousState, snapshotAfter, Map.of());
    }

    public static EventCommand of(
        User actor, EventActionType type, Event event, Map<String, Object> previousState,
        Map<String, Object> snapshotAfter, Map<String, Object> extra) {
        return new EventCommand(actor, type, event, previousState, snapshotAfter, extra == null ? Map.of() : extra);
    }

    /** A command for a brand-new aggregate. */
    public static EventCommand creation(
        User actor, Event event, Map<String, Object> previousState, Map<String, Object> snapshotAfter) {
        return new EventCommand(actor, EventActionType.CREATE_EVENT, event, previousState, snapshotAfter, Map.of());
    }

    /** Human-readable label for the activity feed. */
    public String describe() {
        return "%s %s".formatted(
            actor == null ? "System" : actor.getName(),
            type.description());
    }
}
