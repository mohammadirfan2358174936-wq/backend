package com.example.eventmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * One entry of an event's action stack — the **stack-based event activity**.
 *
 * Backing the `Deque<EventAction>` concept in a real table is what makes the
 * history survive a restart and stay correct across a cluster (an in-memory
 * deque cannot be consulted by another pod, nor by the history endpoint after a
 * deploy).
 *
 * `seq` is a monotonically increasing per-event counter, so "newest action
 * first" is a single indexed query: `ORDER BY seq DESC`.
 */
@Entity
@Table(
    name = "event_actions",
    indexes = {
        @Index(name = "idx_actions_event_seq", columnList = "event_id, seq DESC"),
        @Index(name = "idx_actions_actor", columnList = "actor_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAction extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_actions_event"))
    @ToString.Exclude
    private Event event;

    /** Denormalised so the history endpoint does not join `events`. */
    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private EventActionType actionType;

    @NotBlank
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_actions_actor"))
    @ToString.Exclude
    private User actor;

    /**
     * Full state of the event *before* this action, as JSON. This is what
     * `undo` restores. Null for actions with no reversible state (publish, cancel).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state", columnDefinition = "jsonb")
    private Map<String, Object> previousState;

    /**
     * JSON snapshot of the event *after* the action. Kept so a later
     * `undo → redo` round-trip does not need to recompute anything.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resulting_state", columnDefinition = "jsonb")
    private Map<String, Object> resultingState;

    /**
     * Monotonic per-event counter — the "height" of the stack for this entry.
     * Ascending with time; the max value is the top of the stack.
     */
    @Column(name = "seq", nullable = false)
    private long seq;

    /** Set to true once this action has been reversed by `undo`. */
    @Column(name = "undone", nullable = false)
    @Builder.Default
    private boolean undone = false;

    @Column(name = "undo_reason", length = 300)
    private String undoReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /* ------------------------------------------------------------ behaviour */

    /** Pop it — mark as the current top, still undoable. */
    public void peek() {
        // Intentionally a no-op: `peek` semantics are expressed through queries
        // in the service layer. The method exists to document the stack API.
    }

    /** Pop this entry off the stack: record why and reverse it. */
    public void pop(String reason) {
        this.undone = true;
        this.undoReason = reason;
    }

    public boolean isActive() {
        return !undone;
    }
}
