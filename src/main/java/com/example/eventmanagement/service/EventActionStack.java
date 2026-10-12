package com.example.eventmanagement.service;

import com.example.eventmanagement.entity.EventAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THE EVENT ACTION STACK — `Deque<EventAction>`
 * ============================================
 *
 * A per-event, bounded, LIFO stack of organiser actions:
 *
 * ```
 *   CREATE EVENT ──► UPDATE EVENT ──► CHANGE CAPACITY ──► PUBLISH EVENT
 *                                                          ▲
 *                                                    peekAction()   ← top
 * ```
 *
 * ## Why both a deque and a table?
 *
 * The in-memory deque is the fast, session-local view: reading the top of the
 * stack for the "Undo" button is O(1) and never touches the database.
 *
 * The `event_actions` table is the source of truth. It makes the history
 * queryable by the API, survives a restart, and works across a cluster (an
 * in-memory deque cannot be consulted by another pod, nor by `GET /history`
 * after a redeploy). Both are kept in step by `EventActionService`, which
 * writes the row first and only then pushes onto the deque, so a failed insert
 * can never leave the deque ahead of the database.
 *
 * ## Semantics
 * ```
 * pushAction(a)   → add to the top (after a HERE-dating insert, raise)
 * peekAction()    → newest, without popping
 * popAction()     → remove and return the newest
 * undoLast()      → pop + reverse
 * ```
 * Bounded at `capacity` entries; the oldest silently fall off the bottom.
 */
@Slf4j
@Component
public class EventActionStack {

    /** Per-event stack. Concurrent because two organisers may act at once. */
    private final Map<Long, Deque<EventAction>> stacks = new ConcurrentHashMap<>();

    private final int capacity;

    public EventActionStack(@Value("${app.events.action-stack-capacity:50}") int capacity) {
        this.capacity = capacity;
        log.info("EventActionStack initialised with capacity {} per event", capacity);
    }

    /** **pushAction()** — put an action on the top of the stack. */
    public void pushAction(Long eventId, EventAction action) {
        Deque<EventAction> stack = stacks.computeIfAbsent(eventId, id -> new ArrayDeque<>(capacity));
        synchronized (stack) {
            stack.addFirst(action);
            // Bounded: the oldest actions fall off the bottom of the stack.
            while (stack.size() > capacity) {
                stack.removeLast();
            }
        }
        log.trace("Pushed action {} onto stack for event {}", action.getActionType(), eventId);
    }

    /** **peekAction()** — the newest action, without removing it. */
    public Optional<EventAction> peekAction(Long eventId) {
        Deque<EventAction> stack = stacks.get(eventId);
        if (stack == null) return Optional.empty();
        synchronized (stack) {
            return Optional.ofNullable(stack.peekFirst());
        }
    }

    /** **popAction()** — remove and return the newest action. */
    public Optional<EventAction> popAction(Long eventId) {
        Deque<EventAction> stack = stacks.get(eventId);
        if (stack == null) return Optional.empty();
        synchronized (stack) {
            Optional<EventAction> popped = Optional.ofNullable(stack.pollFirst());
            if (stack.isEmpty()) stacks.remove(eventId);
            return popped;
        }
    }

    /** Snapshot of the stack, newest first (LIFO read order). */
    public List<EventAction> snapshot(Long eventId) {
        Deque<EventAction> stack = stacks.get(eventId);
        if (stack == null) return List.of();
        synchronized (stack) {
            return new ArrayList<>(stack);
        }
    }

    /** Height of the stack — the depth of undo available for this event. */
    public int size(Long eventId) {
        Deque<EventAction> stack = stacks.get(eventId);
        if (stack == null) return 0;
        synchronized (stack) {
            return stack.size();
        }
    }

    /** Drop the in-memory view (used on delete, and by tests). */
    public void clear(Long eventId) {
        stacks.remove(eventId);
    }

    public void clearAll() {
        stacks.clear();
    }

    public int getCapacity() {
        return capacity;
    }

    /* ------------------------------------------------- helpers for warm-up */

    /**
     * Rebuild the in-memory deque from persisted history. Called when an event
     * is first touched after a restart or by another node, so the "Undo"
     * button works without waiting for a full history query.
     */
    public void warm(Long eventId, List<EventAction> persistedNewestFirst) {
        Deque<EventAction> stack = new ArrayDeque<>(capacity);
        for (EventAction action : persistedNewestFirst) {
            if (action.isActive()) stack.addFirst(action);
        }
        if (stack.isEmpty()) {
            stacks.remove(eventId);
            return;
        }
        stacks.put(eventId, stack);
    }

    /** True when the stack was never hydrated for this event. */
    public boolean isCold(Long eventId) {
        Deque<EventAction> stack = stacks.get(eventId);
        return stack == null || stack.isEmpty();
    }

    /** Reference point for "now" — injectable so tests are deterministic. */
    public Instant now() {
        return Instant.now();
    }
}
