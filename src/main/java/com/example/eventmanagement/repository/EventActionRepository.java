package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.EventAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ACTION STACK REPOSITORY
 * -----------------------
 * The database-backed half of the stack-based event activity feature.
 *
 * The stack invariant "latest action first" is implemented with the monotonic
 * per-event `seq` column and the composite index
 * `idx_actions_event_seq (event_id, seq DESC)`, so `peek` and `pop` are both
 * O(log n) index seeks.
 */
@Repository
public interface EventActionRepository extends JpaRepository<EventAction, Long> {

    /** **peekAction()** — the newest action that has not been undone. */
    @Query("""
        select a from EventAction a
        left join fetch a.event e
        left join fetch a.actor u
        where a.event.id = :eventId and a.undone = false
        order by a.seq desc
        """)
    List<EventAction> findNewestNotUndone(@Param("eventId") Long eventId, Pageable pageable);

    default Optional<EventAction> peek(@Param("eventId") Long eventId) {
        List<EventAction> top = findNewestNotUndone(eventId, Pageable.ofSize(1));
        return top.isEmpty() ? Optional.empty() : Optional.of(top.get(0));
    }

    /** Full history, LIFO: newest action first. */
    @Query("""
        select a from EventAction a
        left join fetch a.event e
        left join fetch a.actor u
        where a.event.id = :eventId
        order by a.seq desc
        """)
    List<EventAction> findHistory(@Param("eventId") Long eventId);

    @Query("select coalesce(max(a.seq), 0) from EventAction a where a.event.id = :eventId")
    long findMaxSeq(@Param("eventId") Long eventId);

    @Query("""
        select a from EventAction a
        left join fetch a.event e
        left join fetch a.actor u
        order by a.createdAt desc
        """)
    List<EventAction> findRecent(Pageable pageable);

    @Query("""
        select a from EventAction a
        left join fetch a.event e
        left join fetch a.actor u
        where a.actor.id = :actorId
        order by a.seq desc
        """)
    List<EventAction> findByActor(@Param("actorId") Long actorId);

    long countByUndoneFalse();
}
