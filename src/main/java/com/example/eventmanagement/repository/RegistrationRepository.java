package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.Registration;
import com.example.eventmanagement.entity.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /**
     * Duplicate-booking guard. The DB also enforces
     * `UNIQUE(user_id, event_id)` — this check exists to fail fast with a clean
     * 409 rather than surfacing a constraint violation, and never as the
     * *only* line of defence.
     */
    @Query("""
        select r from Registration r
        where r.user.id = :userId and r.event.id = :eventId
          and r.status in :activeStatuses
        """)
    Optional<Registration> findActive(@Param("userId") Long userId,
                                     @Param("eventId") Long eventId,
                                     @Param("activeStatuses") Collection<RegistrationStatus> activeStatuses);

    Optional<Registration> findByUserIdAndEventId(Long userId, Long eventId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    long countByEventIdAndStatusIn(Long eventId, Collection<RegistrationStatus> statuses);

    @Query("""
        select distinct r from Registration r
        join fetch r.event e
        left join fetch e.organizer o
        left join fetch e.category c
        where r.user.id = :userId and r.status in :statuses
        order by r.createdAt desc
        """)
    List<Registration> findMyEvents(@Param("userId") Long userId,
                                   @Param("statuses") Collection<RegistrationStatus> statuses,
                                   Pageable pageable);

    @Query(
        value = """
            select distinct r from Registration r
            join fetch r.event e
            left join fetch e.organizer o
            left join fetch e.category c
            where r.user.id = :userId and r.status in :statuses
            order by r.createdAt desc
            """,
        countQuery = """
            select count(r) from Registration r
            where r.user.id = :userId and r.status in :statuses
            """)
    Page<Registration> findMyEventsPaged(@Param("userId") Long userId,
                                        @Param("statuses") Collection<RegistrationStatus> statuses,
                                        Pageable pageable);

    @Query(
        value = """
            select distinct r from Registration r
            join fetch r.event e
            left join fetch e.organizer o
            left join fetch e.category c
            where r.user.id = :userId
            order by r.createdAt desc
            """,
        countQuery = "select count(r) from Registration r where r.user.id = :userId")
    Page<Registration> findByUserPaged(@Param("userId") Long userId, Pageable pageable);

    @Query(
        value = """
            select distinct r from Registration r
            join fetch r.event e
            left join fetch e.organizer o
            left join fetch e.category c
            where r.event.organizer.id = :organizerId
            order by r.createdAt desc
            """,
        countQuery = "select count(r) from Registration r where r.event.organizer.id = :organizerId")
    Page<Registration> findByOrganizerPaged(@Param("organizerId") Long organizerId, Pageable pageable);

    /* ------------------------------------------------------------- dashboard */

    @Query("select coalesce(sum(r.totalAmount), 0) from Registration r where r.status = :status")
    double sumRevenueByStatus(@Param("status") RegistrationStatus status);

    @Query("""
        select coalesce(sum(r.totalAmount), 0) from Registration r
        where r.event.organizer.id = :organizerId and r.status = :status
        """)
    double sumRevenueForOrganizer(@Param("organizerId") Long organizerId, @Param("status") RegistrationStatus status);

    @Query("select coalesce(sum(r.quantity), 0) from Registration r where r.status = :status")
    long countSeatsByStatus(@Param("status") RegistrationStatus status);

    @Query("""
        select coalesce(sum(r.quantity), 0) from Registration r
        where r.event.organizer.id = :organizerId and r.status = :status
        """)
    long countSeatsForOrganizer(@Param("organizerId") Long organizerId, @Param("status") RegistrationStatus status);

    @Query("select count(r) from Registration r where r.createdAt >= :since and r.status = :status")
    long countSince(@Param("since") Instant since, @Param("status") RegistrationStatus status);

    /**
     * Monthly revenue + booking trend, computed with a single grouped query.
     * `function('to_char', ...)` keeps this portable across the two supported
     * dialects while still producing a `YYYY-MM` bucket on both.
     */
    @Query("""
        select function('to_char', r.createdAt, 'YYYY-MM') as month,
               coalesce(sum(r.totalAmount), 0) as revenue,
               coalesce(sum(r.quantity), 0) as registrations
        from Registration r
        where r.status = :status and r.createdAt >= :since
        group by function('to_char', r.createdAt, 'YYYY-MM')
        order by month asc
        """)
    List<MonthlyTrend> monthlyTrend(@Param("status") RegistrationStatus status, @Param("since") Instant since);

    @Query("""
        select function('to_char', r.createdAt, 'YYYY-MM') as month,
               coalesce(sum(r.totalAmount), 0) as revenue,
               coalesce(sum(r.quantity), 0) as registrations
        from Registration r
        where r.event.organizer.id = :organizerId and r.status = :status and r.createdAt >= :since
        group by function('to_char', r.createdAt, 'YYYY-MM')
        order by month asc
        """)
    List<MonthlyTrend> monthlyTrendForOrganizer(@Param("organizerId") Long organizerId,
                                                @Param("status") RegistrationStatus status,
                                                @Param("since") Instant since);

    /** Top events by revenue for one organiser. */
    @Query("""
        select r.event.id as eventId,
               coalesce(sum(r.totalAmount), 0) as revenue,
               coalesce(sum(r.quantity), 0) as registrations
        from Registration r
        where r.event.organizer.id = :organizerId and r.status = :status
        group by r.event.id
        order by coalesce(sum(r.totalAmount), 0) desc
        """)
    List<EventRevenue> revenueByEventForOrganizer(@Param("organizerId") Long organizerId,
                                                 @Param("status") RegistrationStatus status,
                                                 Pageable pageable);

    /** Recent bookings platform-wide — the admin activity feed. */
    @Query(
        value = """
            select r from Registration r
            join fetch r.event e
            left join fetch e.category c
            order by r.createdAt desc
            """,
        countQuery = "select count(r) from Registration r")
    Page<Registration> findRecent(Pageable pageable);

    interface MonthlyTrend {
        String getMonth();

        Double getRevenue();

        Long getRegistrations();
    }

    interface EventRevenue {
        Long getEventId();

        Double getRevenue();

        Long getRegistrations();
    }
}
