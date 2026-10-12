package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * EVENT REPOSITORY
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    /* ------------------------------------------------------------- lockings */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.slug = :slug")
    Optional<Event> findBySlugForUpdate(@Param("slug") String slug);

    /* -------------------------------------------------------------- lookups */

    /**
     * The card-grid query. `coalesce` in the ORDER BY keeps null-priced events
     * (free events) at the end of a price sort rather than the start, which
     * would otherwise be the database default.
     */
    @Query(
        value = """
            select e from Event e
            left join fetch e.organizer o
            left join fetch e.category c
            where e.deleted = false
              and (:status is null or e.status = :status)
              and e.status in :visibleStatuses
              and (:categorySlug is null or c.slug = :categorySlug)
              and (:city is null or lower(e.city) like lower(concat('%', cast(:city as string), '%')))
              and (:query is null or lower(e.title) like lower(concat('%', cast(:query as string), '%'))
                                  or lower(e.description) like lower(concat('%', cast(:query as string), '%')))
              and (:minPrice is null or e.price >= :minPrice)
              and (:maxPrice is null or e.price <= :maxPrice)
              and (:from is null or e.startAt >= :from)
              and (:to is null or e.startAt <= :to)
              and (:featured is null or e.featured = :featured)
            order by
              case when :sortAsc = true and :sortField = 'startAt' then e.startAt end asc,
              case when :sortAsc = false and :sortField = 'startAt' then e.startAt end desc,
              case when :sortAsc = true and :sortField = 'price' then coalesce(e.price, 999999) end asc,
              case when :sortAsc = false and :sortField = 'price' then coalesce(e.price, -1) end desc,
              case when :sortAsc = true and :sortField = 'title' then e.title end asc,
              case when :sortAsc = false and :sortField = 'title' then e.title end desc,
              case when :sortAsc = true and :sortField = 'createdAt' then e.createdAt end asc,
              case when :sortAsc = false and :sortField = 'createdAt' then e.createdAt end desc,
              e.featured desc, e.startAt asc
            """,
        countQuery = """
            select count(e) from Event e
            where e.deleted = false
              and (:status is null or e.status = :status)
              and e.status in :visibleStatuses
              and (:categorySlug is null or e.category.slug = :categorySlug)
              and (:city is null or lower(e.city) like lower(concat('%', cast(:city as string), '%')))
              and (:query is null or lower(e.title) like lower(concat('%', cast(:query as string), '%'))
                                  or lower(e.description) like lower(concat('%', cast(:query as string), '%')))
              and (:minPrice is null or e.price >= :minPrice)
              and (:maxPrice is null or e.price <= :maxPrice)
              and (:from is null or e.startAt >= :from)
              and (:to is null or e.startAt <= :to)
              and (:featured is null or e.featured = :featured)
            """)
    Page<Event> searchCards(
        @Param("status") EventStatus status,
        @Param("visibleStatuses") Collection<EventStatus> visibleStatuses,
        @Param("categorySlug") String categorySlug,
        @Param("city") String city,
        @Param("query") String query,
        @Param("minPrice") Double minPrice,
        @Param("maxPrice") Double maxPrice,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("featured") Boolean featured,
        @Param("sortField") String sortField,
        @Param("sortAsc") Boolean sortAsc,
        Pageable pageable);

    /** Featured events for the landing page (cached — see `CacheConfig`). */
    @Query("""
        select e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        where e.deleted = false
          and e.featured = true
          and e.status = com.example.eventmanagement.entity.EventStatus.PUBLISHED
          and e.startAt >= :now
        order by e.startAt asc
        """)
    List<Event> findFeatured(@Param("now") Instant now, Pageable pageable);

    /** `GET /events/{id}` — the full detail slice, fetched in one statement. */
    @Query("""
        select distinct e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        left join fetch e.tickets t
        left join fetch e.speakers s
        left join fetch e.agenda a
        left join fetch e.reviews r
        left join fetch e.tags tg
        where e.deleted = false and e.id = :id
        """)
    Optional<Event> findDetailById(@Param("id") Long id);

    @Query("""
        select distinct e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        left join fetch e.tickets t
        left join fetch e.speakers s
        left join fetch e.agenda a
        left join fetch e.reviews r
        left join fetch e.tags tg
        where e.deleted = false and e.slug = :slug
        """)
    Optional<Event> findDetailBySlug(@Param("slug") String slug);

    /* ----------------------------------------------------------- organiser */

    @Query("""
        select e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        where e.deleted = false and e.organizer.id = :organizerId
        order by e.startAt asc
        """)
    List<Event> findByOrganizer(@Param("organizerId") Long organizerId);

    @Query("""
        select e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        where e.deleted = false and e.organizer.id = :organizerId
          and e.status = com.example.eventmanagement.entity.EventStatus.PUBLISHED
          and e.startAt >= :now
        order by e.startAt asc
        """)
    List<Event> findUpcomingForOrganizer(@Param("organizerId") Long organizerId, @Param("now") Instant now);

    @Query("""
        select e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        where e.deleted = false and e.organizer.id = :organizerId and e.id in :ids
        """)
    List<Event> findByOrganizerAndIds(@Param("organizerId") Long organizerId, @Param("ids") Collection<Long> ids);

    long countByOrganizerIdAndDeletedFalse(Long organizerId);

    /* ------------------------------------------------------------- lifecycle */

    /** Events that already ended and are still marked PUBLISHED → COMPLETED. */
    @Query("""
        select e from Event e
        where e.deleted = false and e.status = com.example.eventmanagement.entity.EventStatus.PUBLISHED
          and e.endAt < :now
        """)
    List<Event> findToComplete(@Param("now") Instant now);

    boolean existsBySlug(String slug);

    Optional<Event> findBySlug(String slug);

    /* -------------------------------------------------------------- aggregates */

    /** `sum(capacity)` over every live event — admin dashboard. */
    @Query("select coalesce(sum(e.registrationCount), 0) from Event e where e.deleted = false")
    long sumRegistrations();

    @Query("select count(e) from Event e where e.deleted = false and e.status = :status")
    long countByStatus(@Param("status") EventStatus status);

    @Query("""
        select e from Event e
        left join fetch e.organizer o
        left join fetch e.category c
        where e.deleted = false and e.status = com.example.eventmanagement.entity.EventStatus.PUBLISHED
        order by e.registrationCount desc
        """)
    List<Event> findTopByRegistrations(Pageable pageable);

    Page<Event> findByDeletedFalse(Pageable pageable);
}
