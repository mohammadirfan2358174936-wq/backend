package com.example.eventmanagement.service;

import com.example.eventmanagement.dto.request.CreateEventRequest;
import com.example.eventmanagement.dto.request.EventQueryParams;
import com.example.eventmanagement.dto.request.UpdateEventRequest;
import com.example.eventmanagement.dto.response.EventCardResponse;
import com.example.eventmanagement.dto.response.EventDetailResponse;
import com.example.eventmanagement.entity.Category;
import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventActionType;
import com.example.eventmanagement.entity.EventStatus;
import com.example.eventmanagement.entity.TicketTier;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.exception.CategoryNotFoundException;
import com.example.eventmanagement.exception.EventNotFoundException;
import com.example.eventmanagement.exception.InvalidDateRangeException;
import com.example.eventmanagement.exception.InvalidEventStateException;
import com.example.eventmanagement.exception.NotEventOwnerException;
import com.example.eventmanagement.mapper.DtoMapper;
import com.example.eventmanagement.repository.CategoryRepository;
import com.example.eventmanagement.repository.EventRepository;
import com.example.eventmanagement.repository.UserRepository;
import com.example.eventmanagement.util.ApiResponse;
import com.example.eventmanagement.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EVENT SERVICE
 * =============
 * Owns the event aggregate: creation, editing, publishing, cancelling, deletion
 * and discovery.
 *
 * Every mutating method:
 *  1. takes a snapshot **before** the mutation,
 *  2. applies the change,
 *  3. records an `EventCommand` — which becomes an `EventAction` on the stack.
 *
 * That ordering is what powers the `undo` endpoint: without the pre-mutation
 * snapshot, reversal would have to guess the previous value.
 *
 * Caching: reads are `@Cacheable`/evicted on writes, so a hot discovery page
 * does not hit the database on every request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final Set<EventStatus> PUBLIC_VISIBLE =
        EnumSet.of(EventStatus.PUBLISHED, EventStatus.COMPLETED);

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final EventActionService actionService;
    private final EventActionSnapshotter snapshotter;
    private final DtoMapper mapper;
    private final SlugGenerator slugGenerator;

    /* ------------------------------------------------------------- discovery */

    /**
     * Paged discovery.
     *
     * The status filter is applied in two layers: a requested narrows further,
     * while `visibleStatuses` enforces that anonymous callers never see drafts.
     */
    @Transactional(readOnly = true)
    public ApiResponse.PagedBody<EventCardResponse> discover(EventQueryParams params, User actor) {
        int size = params.sizeOrDefault(12, 100);
        String sortField = params.sortField();
        Sort.Direction direction = params.sortAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(
            params.pageOrZero(),
            size,
            Sort.by(direction, sortField));

        Set<EventStatus> visible = visibilityFor(actor, params.status());

        Specification<Event> spec = (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("organizer", JoinType.LEFT);
                root.fetch("category", JoinType.LEFT);
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (visible != null && !visible.isEmpty()) {
                predicates.add(root.get("status").in(visible));
            }
            if (params.status() != null) {
                predicates.add(cb.equal(root.get("status"), params.status()));
            }
            if (params.category() != null && !params.category().isBlank()) {
                predicates.add(cb.equal(root.get("category").get("slug"), params.category().trim()));
            }
            if (params.city() != null && !params.city().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("city")), "%" + params.city().toLowerCase().trim() + "%"));
            }
            if (params.q() != null && !params.q().isBlank()) {
                String search = "%" + params.q().toLowerCase().trim() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), search),
                    cb.like(cb.lower(root.get("description")), search)
                ));
            }
            if (params.minPrice() != null) {
                predicates.add(cb.ge(root.get("price"), params.minPrice()));
            }
            if (params.maxPrice() != null) {
                predicates.add(cb.le(root.get("price"), params.maxPrice()));
            }
            if (params.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startAt"), params.from()));
            }
            if (params.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startAt"), params.to()));
            }
            if (params.featured() != null) {
                predicates.add(cb.equal(root.get("featured"), params.featured()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Event> page = eventRepository.findAll(spec, pageable);

        return ApiResponse.paged(page, mapper::toCard,
            "Found %d event(s)".formatted(page.getTotalElements()));
    }

    /** Featured landing-page events, cached for 10 minutes. */
    @Cacheable(value = "featured-events", key = "#size", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<EventCardResponse> featured(int size) {
        return mapper.toCards(eventRepository.findFeatured(Instant.now(), PageRequest.of(0, Math.max(1, size))));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse detail(Long id, User actor) {
        Event event = eventRepository.findDetailById(id)
            .orElseThrow(() -> new EventNotFoundException(id));
        if (event.isDeleted() || !visibilityFor(actor, null).contains(event.getStatus())) {
            throw new EventNotFoundException(id);
        }
        return mapper.toDetail(event);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse detailBySlug(String slug) {
        Event event = eventRepository.findDetailBySlug(slug)
            .orElseThrow(() -> new EventNotFoundException(slug));
        return mapper.toDetail(event);
    }

    private Set<EventStatus> visibilityFor(User actor, EventStatus requested) {
        if (actor == null || actor.getRole().name().equals("ATTENDEE")) {
            // Anonymous / attendees never see drafts or cancellations.
            if (requested == EventStatus.DRAFT || requested == EventStatus.CANCELLED) {
                return PUBLIC_VISIBLE;
            }
            return PUBLIC_VISIBLE;
        }
        // Organisers and admins see every status they explicitly ask about,
        // plus the public ones.
        Set<EventStatus> all = EnumSet.allOf(EventStatus.class);
        if (requested != null) {
            all.add(requested);
        }
        all.addAll(PUBLIC_VISIBLE);
        return all;
    }

    /* ---------------------------------------------------------------- create */

    /**
     * Create an event.
     *
     * A `CREATE_EVENT` action is pushed with an empty snapshot. Undoing it
     * soft-deletes the event, which is the correct inverse for "this aggregate
     * should not exist".
     */
    @CacheEvict(value = { "featured-events", "categories" }, allEntries = true)
    @Transactional
    public EventDetailResponse create(CreateEventRequest request, User actor) {
        validateDates(request.startAt(), request.endAt());

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        int capacity = request.capacity() == null ? 0 : request.capacity();

        Event event = Event.builder()
            .title(request.title().trim())
            .slug(slugGenerator.uniqueFor(request.title()))
            .description(nullSafe(request.description()))
            .shortDescription(nullSafe(request.shortDescription()))
            .coverImageUrl(request.coverImageUrl())
            .category(category)
            .organizer(actor)
            .startAt(request.startAt())
            .endAt(request.endAt())
            .venueName(request.venueName().trim())
            .addressLine(request.addressLine().trim())
            .city(request.city().trim())
            .country(request.country() == null || request.country().isBlank() ? "India" : request.country().trim())
            .capacity(capacity)
            .availableSeats(capacity)
            .price(request.price() != null ? request.price() : priceFrom(capacity, request.tickets()))
            .status(request.status() == null ? EventStatus.DRAFT : request.status())
            .featured(Boolean.TRUE.equals(request.featured()))
            .tags(request.tags() == null ? List.of() : request.tags())
            .registrationCount(0)
            .build();

        if (event.getStatus() == EventStatus.PUBLISHED) {
            event.setPublishedAt(Instant.now());
        }

        applyTiers(event, request.tickets());

        Event saved = eventRepository.save(event);

        // Snapshot happens after persistence so the id is stable, but describes
        // the state as created — undoing it removes the aggregate entirely.
        actionService.record(EventCommand.creation(
            actor, saved, snapshotter.empty(), snapshotter.snapshot(saved)));

        log.info("{} created event {} ({})", actor.getName(), saved.getId(), saved.getSlug());
        return reload(saved);
    }

    /* ---------------------------------------------------------------- update */

    /**
     * Update an event.
     *
     * Capacity rules:
     *  - cannot shrink below the seats already sold;
     *  - `availableSeats` is recomputed as `capacity - seatsSold`.
     */
    @CacheEvict(value = { "featured-events", "categories" }, allEntries = true)
    @Transactional
    public EventDetailResponse update(Long id, UpdateEventRequest request, User actor) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new EventNotFoundException(id));

        assertOwner(event, actor);

        Instant start = request.startAt() == null ? event.getStartAt() : request.startAt();
        Instant end = request.endAt() == null ? event.getEndAt() : request.endAt();
        validateDates(start, end);

        // --- snapshot BEFORE any mutation -------------------------------
        var snapshot = snapshotter.snapshot(event);
        int previousCapacity = event.getCapacity();
        int sold = event.getRegistrationCount();

        if (request.capacity() != null && request.capacity() < sold) {
            throw new InvalidEventStateException(
                "Cannot reduce capacity to %d — %d seat(s) are already sold."
                    .formatted(request.capacity(), sold));
        }

        applyUpdate(event, request, actor);

        // Preserve the registration invariant if capacity changed.
        if (event.getCapacity() != previousCapacity) {
            event.setAvailableSeats(Math.max(0, event.getCapacity() - sold));
        }

        if (request.tickets() != null) {
            actionService.record(EventCommand.of(actor, EventActionType.ADD_TICKET, event, snapshot, Map.of()));
            applyTiers(event, request.tickets());
        }

        Event saved = eventRepository.save(event);

        actionService.record(EventCommand.of(actor, EventActionType.UPDATE_EVENT, event, snapshot,
            snapshotter.snapshot(saved)));

        log.info("{} updated event {}", actor.getName(), id);
        return reload(saved);
    }

    private void applyUpdate(Event event, UpdateEventRequest request, User actor) {
        if (request.title() != null && !request.title().isBlank()) event.setTitle(request.title().trim());
        if (request.description() != null) event.setDescription(request.description());
        if (request.shortDescription() != null) event.setShortDescription(request.shortDescription());
        if (request.coverImageUrl() != null) event.setCoverImageUrl(request.coverImageUrl());
        if (request.startAt() != null) event.setStartAt(request.startAt());
        if (request.endAt() != null) event.setEndAt(request.endAt());
        if (request.venueName() != null) event.setVenueName(request.venueName().trim());
        if (request.addressLine() != null) event.setAddressLine(request.addressLine().trim());
        if (request.city() != null) event.setCity(request.city().trim());
        if (request.country() != null) event.setCountry(request.country().trim());
        if (request.latitude() != null) event.setLatitude(request.latitude());
        if (request.longitude() != null) event.setLongitude(request.longitude());
        if (request.capacity() != null) event.setCapacity(request.capacity());
        if (request.price() != null) event.setPrice(request.price());
        if (request.featured() != null) event.setFeatured(request.featured());
        if (request.tags() != null) event.setTags(request.tags());

        if (request.categoryId() != null
            && !request.categoryId().equals(event.getCategory().getId())) {
            Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
            event.setCategory(category);
        }
    }

    /* -------------------------------------------------------------- publish */

    @CacheEvict(value = { "featured-events", "categories" }, allEntries = true)
    @Transactional
    public EventDetailResponse publish(Long id, User actor) {
        Event event = eventRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new EventNotFoundException(id));

        assertOwner(event, actor);

        var snapshot = snapshotter.snapshot(event);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException(
                "Only draft events can be published (this one is %s).".formatted(event.getStatus()));
        }
        if (event.getCapacity() <= 0) {
            throw new InvalidEventStateException("Add a capacity before publishing.");
        }
        if (!event.getTickets().isEmpty() && event.getTickets().stream().allMatch(t -> !t.isActive())) {
            throw new InvalidEventStateException("Add at least one active ticket tier before publishing.");
        }

        event.publish();
        Event saved = eventRepository.save(event);

        actionService.record(EventCommand.of(actor, EventActionType.PUBLISH_EVENT, event, snapshot,
            snapshotter.snapshot(saved)));

        log.info("{} published event {}", actor.getName(), id);
        return reload(saved);
    }

    /* --------------------------------------------------------------- cancel */

    @CacheEvict(value = { "featured-events", "categories" }, allEntries = true)
    @Transactional
    public EventDetailResponse cancel(Long id, User actor) {
        Event event = eventRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new EventNotFoundException(id));

        assertOwner(event, actor);

        if (event.getStatus() == EventStatus.CANCELLED || event.isCancelled()) {
            throw new InvalidEventStateException("This event is already cancelled.");
        }

        var snapshot = snapshotter.snapshot(event);
        event.cancel();
        Event saved = eventRepository.save(event);

        actionService.record(EventCommand.of(actor, EventActionType.CANCEL_EVENT, event, snapshot,
            snapshotter.snapshot(saved)));

        log.info("{} cancelled event {}", actor.getName(), id);
        return reload(saved);
    }

    /* --------------------------------------------------------------- delete */

    /**
     * Soft delete. Events that already hold bookings are never hard-deleted —
     * attendees need their tickets to stay resolvable, and the audit trail on
     * `event_actions` would dangle.
     */
    @CacheEvict(value = { "featured-events", "categories" }, allEntries = true)
    @Transactional
    public void delete(Long id, User actor) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new EventNotFoundException(id));

        assertOwner(event, actor);

        var snapshot = snapshotter.snapshot(event);
        event.softDelete();
        eventRepository.save(event);

        actionService.record(EventCommand.of(actor, EventActionType.DELETE_EVENT, event, snapshot,
            snapshotter.snapshot(event)));

        log.info("{} soft-deleted event {}", actor.getName(), id);
    }

    /* -------------------------------------------------------------- capacity */

    /**
     * CHANGE_CAPACITY — an explicit capacity edit, tracked as its own stack
     * entry so `undo` can restore the previous figure in one step.
     */
    @CacheEvict(value = { "featured-events" }, allEntries = true)
    @Transactional
    public EventDetailResponse changeCapacity(Long id, int newCapacity, User actor) {
        Event event = eventRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new EventNotFoundException(id));

        assertOwner(event, actor);

        int sold = event.getRegistrationCount();
        if (newCapacity < sold) {
            throw new InvalidEventStateException(
                "Capacity %d is below the %d seat(s) already sold.".formatted(newCapacity, sold));
        }

        var snapshot = snapshotter.snapshot(event);
        event.setCapacity(newCapacity);
        event.setAvailableSeats(Math.max(0, newCapacity - sold));
        Event saved = eventRepository.save(event);

        actionService.record(EventCommand.of(
            actor, EventActionType.CHANGE_CAPACITY, event, snapshot, snapshotter.snapshot(saved),
            Map.of("previousCapacity", event.getCapacity(), "newCapacity", newCapacity)));

        log.info("{} changed capacity of event {} to {}", actor.getName(), id, newCapacity);
        return reload(saved);
    }

    /* ----------------------------------------------------------- organiser */

    @Transactional(readOnly = true)
    public List<EventCardResponse> forOrganizer(Long organizerId) {
        return mapper.toCards(eventRepository.findByOrganizer(organizerId));
    }

    @Transactional(readOnly = true)
    public List<EventCardResponse> upcomingForOrganizer(Long organizerId, int limit) {
        return mapper.toCards(eventRepository.findUpcomingForOrganizer(
            organizerId, Instant.now()).stream().limit(Math.max(1, limit)).toList());
    }

    /* ----------------------------------------------------------- permission */

    /** Owners and admins may mutate; everybody else is rejected. */
    public void assertOwner(Event event, User actor) {
        if (actor == null) {
            throw new NotEventOwnerException();
        }
        boolean isAdmin = actor.getRole() == com.example.eventmanagement.entity.Role.ADMIN;
        boolean isOwner = event.getOrganizer() != null
            && event.getOrganizer().getId().equals(actor.getId());

        if (!isAdmin && !isOwner) {
            throw new NotEventOwnerException();
        }
    }

    /* ---------------------------------------------------------------- helpers */

    /** Re-fetch with the full detail graph so the response is complete. */
    private EventDetailResponse reload(Event event) {
        return eventRepository.findDetailById(event.getId())
            .map(mapper::toDetail)
            .orElseGet(() -> mapper.toDetail(event));
    }

    private void validateDates(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidDateRangeException();
        }
    }

    private void applyTiers(Event event, List<CreateEventRequest.TicketTierRequest> requests) {
        if (requests != null && !requests.isEmpty()) {
            for (CreateEventRequest.TicketTierRequest tier : requests) {
                event.getTickets().add(TicketTier.builder()
                    .name(tier.name().trim())
                    .description(tier.description())
                    .price(tier.price() == null ? 0.0 : tier.price())
                    .quantityTotal(tier.quantityTotal() != null ? tier.quantityTotal() : event.getCapacity())
                    .quantitySold(0)
                    .maxPerOrder(tier.maxPerOrder() == null || tier.maxPerOrder() <= 0 ? 10 : tier.maxPerOrder())
                    .active(true)
                    .event(event)
                    .build());
            }
        } else if (event.getCapacity() > 0) {
            // Automatically add default general admission tier with event capacity & price
            event.getTickets().add(TicketTier.builder()
                .name("General Admission")
                .description("Standard event entry ticket")
                .price(event.getPrice())
                .quantityTotal(event.getCapacity())
                .quantitySold(0)
                .maxPerOrder(Math.min(10, event.getCapacity()))
                .active(true)
                .event(event)
                .build());
        }
    }

    /**
     * Starting price. Falls back to the cheapest active tier when no explicit
     * price is given, so the card always shows a truthful figure.
     */
    private double priceFrom(int capacity, List<CreateEventRequest.TicketTierRequest> tiers) {
        if (tiers != null && !tiers.isEmpty()) {
            return tiers.stream()
                .mapToDouble(t -> t.price() != null ? t.price() : 0.0)
                .min()
                .orElse(0.0);
        }
        return 0.0;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
