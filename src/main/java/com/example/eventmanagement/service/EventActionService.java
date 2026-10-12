package com.example.eventmanagement.service;

import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventAction;
import com.example.eventmanagement.entity.EventActionType;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.exception.NothingToUndoException;
import com.example.eventmanagement.exception.ConcurrentModificationException;
import com.example.eventmanagement.mapper.DtoMapper;
import com.example.eventmanagement.repository.EventActionRepository;
import com.example.eventmanagement.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EVENT ACTION SERVICE — the stack API
 * ==================================
 *
 * Implements {@link EventActionStackService}: coordinates the two halves of
 * the stack feature.
 *
 * ```
 *  EventActionService  ──►  EventActionStack   (ArrayDeque, hot, O(1) top)
 *         │
 *         └──►  EventActionRepository  (event_actions table, durable)
 * ```
 *
 * ## The stack contract
 *
 * | Operation | Behaviour |
 * |---|---|
 * | `record(cmd)` | `pushAction` — appends to the table, then the deque |
 * | `peek(eventId)` | `peekAction` — newest undoable action, no removal |
 * | `undo(eventId)` | `popAction` — pops, reverses, marks `undone = true` |
 * | `history(eventId)` | LIFO list, newest first |
 *
 * ## Undo semantics
 *
 * Only the **top** of the stack can be undone, which is what makes it a stack
 * rather than a log: undoing an older action while newer ones stand would
 * leave the aggregate inconsistent. `undo` therefore reverses the newest
 * *active* action and stops.
 *
 * The inverse depends on the action type:
 *
 * | Action | Inverse |
 * |---|---|
 * | `CREATE_EVENT` | soft-delete the event |
 * | `UPDATE_EVENT` | restore `previousState` |
 * | `CHANGE_CAPACITY` | restore `previousState.capacity` |
 * | `PUBLISH_EVENT` | revert to `DRAFT` |
 * | `CANCEL_EVENT` | revert `PUBLISHED` |
 * | `DELETE_EVENT` | clear the soft-delete flag |
 *
 * `previousState` is snapshotted *before* the mutation by the caller (see
 * `EventService`), so a restore never has to guess what changed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventActionService implements EventActionStackService {

    private final EventActionRepository actionRepository;
    private final EventActionStack actionStack;
    private final EventRepository eventRepository;
    private final EventActionSnapshotter snapshotter;
    private final DtoMapper mapper;

    @Override
    public EventAction pushAction(EventCommand command) {
        return record(command);
    }

    @Override
    public Optional<EventAction> peekAction(Long eventId) {
        return peek(eventId);
    }

    @Override
    public com.example.eventmanagement.dto.response.EventActionResponse popAction(Long eventId, User actor) {
        return undo(eventId, actor);
    }

    /* ------------------------------------------------------------- pushing */

    /**
     * **pushAction()** — persist then push.
     *
     * Written in this order so the durable store can never be behind the
     * in-memory deque: a failed insert aborts the transaction and nothing is
     * pushed, while a failed push only costs a warm-up on the next read.
     */
    @Transactional
    public EventAction record(EventCommand command) {
        Event event = command.event();
        long nextSeq = actionRepository.findMaxSeq(event.getId()) + 1;

        EventAction action = EventAction.builder()
            .event(event)
            .eventTitle(event.getTitle())
            .actionType(command.type())
            .description(command.describe())
            .actor(command.actor())
            .previousState(command.previousState())
            .resultingState(command.snapshotAfter())
            .seq(nextSeq)
            .undone(false)
            .build();

        EventAction saved = actionRepository.saveAndFlush(action);
        actionStack.pushAction(event.getId(), saved);

        log.debug("Pushed {} on {} (seq={})", command.type(), event.getId(), nextSeq);
        return saved;
    }

    /* -------------------------------------------------------------- peeking */

    /**
     * **peekAction()** — the top of the stack.
     *
     * Served from the deque when it is warm; otherwise the deque is warmed from
     * the database (the source of truth) and then read, so a cold start or a
     * second node behaves exactly like a hot one.
     */
    @Transactional(readOnly = true)
    public Optional<EventAction> peek(Long eventId) {
        if (actionStack.isCold(eventId)) {
            warm(eventId);
        }
        return actionStack.peekAction(eventId);
    }

    /** Depth of undo available. */
    @Transactional(readOnly = true)
    public int depth(Long eventId) {
        if (actionStack.isCold(eventId)) warm(eventId);
        return actionStack.size(eventId);
    }

    /* --------------------------------------------------------------- history */

    /** Full history, newest first. */
    @Transactional(readOnly = true)
    public List<com.example.eventmanagement.dto.response.EventActionResponse> history(Long eventId) {
        return mapper.toActions(actionRepository.findHistory(eventId));
    }

    /* ----------------------------------------------------------------- undo */

    /**
     * **popAction()** — undo the latest operation, LIFO.
     *
     * Runs in its own transaction with the event locked, because it both reads
     * the stack and mutates the aggregate.
     */
    @Transactional
    public com.example.eventmanagement.dto.response.EventActionResponse undo(Long eventId, User actor) {
        // 1. pop the top
        Optional<EventAction> popped = popTop(eventId, actor);
        if (popped.isEmpty()) {
            throw new NothingToUndoException(eventId);
        }
        EventAction action = popped.get();

        // 2. take the write lock before reversing
        Event event = eventRepository.findByIdForUpdate(eventId)
            .orElseThrow(() -> new com.example.eventmanagement.exception.EventNotFoundException(eventId));

        if (event.getVersion() != null && action.getSeq() > 0
            && action.getResultingState() != null
            && !action.getResultingState().isEmpty()
            && stateChangedSince(action, event)) {
            throw new ConcurrentModificationException(
                "Event %d changed after this action was recorded. Reload to continue.".formatted(eventId));
        }

        reverse(action, event);

        // 3. mark the entry as popped off the stack
        action.pop("Undone by %s".formatted(actor.getName()));
        actionRepository.save(action);
        actionStack.popAction(eventId);

        eventRepository.save(event);
        log.info("Undid action {} on event {} (seq={})", action.getActionType(), eventId, action.getSeq());

        pushAfterUndo(action, event, actor);
        return mapper.toAction(action);
    }

    /**
     * Undo is itself an action, so it lands on the stack and can be inspected
     * (and re-reversed) from the history view.
     */
    private void pushAfterUndo(EventAction popped, Event event, User actor) {
        Map<String, Object> before = snapshotter.snapshot(event);
        EventCommand reverseCommand = EventCommand.of(
            actor,
            reverseTypeFor(popped.getActionType()),
            event,
            before,
            snapshotter.snapshot(event),
            Map.of("undid", "action #%d (%s)".formatted(popped.getId(), popped.getActionType())));
        record(reverseCommand);
    }

    /** Pop the newest active action, from the deque or — if cold — the table. */
    private Optional<EventAction> popTop(Long eventId, User actor) {
        if (actionStack.isCold(eventId)) {
            warm(eventId);
        }
        Optional<EventAction> top = actionStack.popAction(eventId);
        if (top.isPresent() && top.get().isActive()) {
            return top;
        }
        // The deque may have been ahead of the table; fall back to the table.
        if (top.isPresent()) {
            actionStack.pushAction(eventId, top.get());
        }
        return actionRepository.peek(eventId);
    }

    private EventActionType reverseTypeFor(EventActionType type) {
        return switch (type) {
            case CREATE_EVENT -> EventActionType.DELETE_EVENT;
            case DELETE_EVENT -> EventActionType.CREATE_EVENT;
            case PUBLISH_EVENT -> EventActionType.CANCEL_EVENT;
            case CANCEL_EVENT -> EventActionType.PUBLISH_EVENT;
            case CHANGE_CAPACITY -> EventActionType.CHANGE_CAPACITY;
            default -> EventActionType.UPDATE_EVENT;
        };
    }

    private boolean stateChangedSince(EventAction action, Event current) {
        Map<String, Object> after = action.getResultingState();
        if (after == null) return false;
        Object snapshottedCapacity = after.get("capacity");
        Object snapshotPrice = after.get("price");
        boolean capacityDrift = snapshottedCapacity instanceof Number n
            && current.getCapacity() != null && current.getCapacity() != n.intValue();
        boolean priceDrift = snapshotPrice instanceof Number n
            && current.getPrice() != null && Math.abs(current.getPrice() - n.doubleValue()) > 0.001;
        return capacityDrift || priceDrift;
    }

    /* --------------------------------------------------------------- reverse */

    private void reverse(EventAction action, Event event) {
        Map<String, Object> snapshot = action.getPreviousState();

        switch (action.getActionType()) {
            case CREATE_EVENT -> {
                // The event only exists because of this action — remove it.
                event.softDelete();
                eventRepository.save(event);
            }
            case UPDATE_EVENT, CHANGE_CAPACITY -> snapshotter.restore(event, snapshot);

            case PUBLISH_EVENT -> event.setStatus(com.example.eventmanagement.entity.EventStatus.DRAFT);

            case CANCEL_EVENT -> event.setStatus(com.example.eventmanagement.entity.EventStatus.PUBLISHED);

            case DELETE_EVENT -> event.setDeleted(false);

            case ADD_TICKET, UPDATE_TICKET -> snapshotter.restore(event, snapshot);
        }
    }

    /* ---------------------------------------------------------- maintenance */

    /** Hydrate the deque from the table; called when it is cold. */
    @Transactional(readOnly = true)
    public void warm(Long eventId) {
        List<EventAction> persisted = actionRepository.findHistory(eventId).stream()
            .filter(EventAction::isActive)
            .toList();
        actionStack.warm(eventId, persisted);
        log.trace("Warmed action stack for event {} with {} entries", eventId, persisted.size());
    }

    /**
     * Recent actions by one organiser — the organiser dashboard activity feed.
     * Deque first, table fallback, newest-first in both cases.
     */
    @Transactional(readOnly = true)
    public List<com.example.eventmanagement.dto.response.EventActionResponse> historyForActor(
        Long actorId, int limit) {

        List<EventAction> actions = actionRepository.findByActor(actorId);
        return mapper.toActions(limit(actions, limit));
    }

    /** Platform-wide recent actions — the admin dashboard activity feed. */
    @Transactional(readOnly = true)
    public List<com.example.eventmanagement.dto.response.EventActionResponse> recentActions(int limit) {
        return mapper.toActions(limit(
            actionRepository.findRecent(org.springframework.data.domain.Pageable.ofSize(limit)), limit));
    }

    private List<EventAction> limit(List<EventAction> actions, int limit) {
        return actions.size() <= limit ? actions : actions.subList(0, limit);
    }

    /**
     * Trim the table so it does not grow without bound.
     * Called by the nightly scheduler — keeps the newest `keep` actions per
     * event and drops the rest, which can no longer be undone anyway.
     */
    @Transactional
    public int trim(int keep) {
        int removed = 0;
        for (Event event : eventRepository.findToComplete(java.time.Instant.now())) {
            List<EventAction> history = actionRepository.findHistory(event.getId());
            if (history.size() <= keep) continue;
            List<EventAction> stale = history.stream().skip(keep).toList();
            actionRepository.deleteAll(stale);
            removed += stale.size();
        }
        if (removed > 0) {
            log.info("Trimmed {} stale event actions", removed);
        }
        return removed;
    }
}
