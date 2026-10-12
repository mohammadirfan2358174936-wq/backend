package com.example.eventmanagement.service;

/**
 * STACK-BASED EVENT ACTIVITY SERVICE — the `Deque<EventAction>` façade.
 *
 * This is the documented public contract of the feature. `EventActionService`
 * implements it and additionally performs the reversal logic; this interface
 * exists so controllers depend on behaviour rather than implementation, and so
 * an alternative implementation (say, an eventual remote store) can be dropped
 * in without touching a single controller.
 *
 * The contract is the classic stack API:
 *
 * ```
 * pushAction()   — put an action on the top
 * peekAction()   — read the top without removing it
 * popAction()    — remove the top
 * undo()         — pop + reverse the operation
 * history()      — snapshot, top-first
 * ```
 */
public interface EventActionStackService {

    /**
     * **pushAction()** — record an action and place it on the top of the stack.
     *
     * @return the persisted action
     */
    com.example.eventmanagement.entity.EventAction pushAction(EventCommand command);

    /**
     * **peekAction()** — the newest action for this event, without popping it.
     * Empty when the stack is empty (every action already undone).
     */
    java.util.Optional<com.example.eventmanagement.entity.EventAction> peekAction(Long eventId);

    /** Depth of the stack — how many operations can still be undone. */
    int depth(Long eventId);

    /**
     * **popAction()** — undo the latest operation (LIFO).
     *
     * @return a description of the action that was reversed
     * @throws com.example.eventmanagement.exception.NothingToUndoException when the stack is empty
     */
    com.example.eventmanagement.dto.response.EventActionResponse popAction(Long eventId,
                                                                          com.example.eventmanagement.entity.User actor);

    /** Full history, newest first. */
    java.util.List<com.example.eventmanagement.dto.response.EventActionResponse> history(Long eventId);

    java.util.List<com.example.eventmanagement.dto.response.EventActionResponse> historyForActor(Long actorId, int limit);

    java.util.List<com.example.eventmanagement.dto.response.EventActionResponse> recentActions(int limit);
}
