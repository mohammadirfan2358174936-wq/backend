package com.example.eventmanagement.mapper;

import com.example.eventmanagement.dto.response.AdminDashboardResponse.RecentRegistration;
import com.example.eventmanagement.dto.response.CategoryResponse;
import com.example.eventmanagement.dto.response.EventActionResponse;
import com.example.eventmanagement.dto.response.EventCardResponse;
import com.example.eventmanagement.dto.response.EventDetailResponse;
import com.example.eventmanagement.dto.response.OrganizerDashboardResponse;
import com.example.eventmanagement.dto.response.RegistrationResponse;
import com.example.eventmanagement.dto.response.TicketResponse;
import com.example.eventmanagement.dto.response.TicketTierResponse;
import com.example.eventmanagement.dto.response.UserResponse;
import com.example.eventmanagement.entity.Category;
import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventAction;
import com.example.eventmanagement.entity.Registration;
import com.example.eventmanagement.entity.Ticket;
import com.example.eventmanagement.entity.TicketTier;
import com.example.eventmanagement.entity.User;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

/**
 * ENTITY → DTO MAPPER
 * ===================
 * A hand-written mapper rather than MapStruct, on purpose:
 *
 *  1. **It is explicit.** A reflection-magic mapping layer makes it easy to leak
 *     a BCrypt hash or a lazy collection onto the wire. Here every published
 *     field is listed by hand.
 *  2. **It tolerates uninitialised lazy associations.** Mapping happens inside
 *     the service transaction, but `LazyInitializationException` still happens
 *     when a query path forgets a fetch. A `safe()` guard turns that into a
 *     graceful null instead of a 500.
 *  3. **Zero build cost.** No annotation processor added to the module graph.
 *
 * Every method is a pure function of its arguments.
 */
@Component
public class DtoMapper {

    /* --------------------------------------------------------------- user */

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPhone(),
            user.getRole().name(),
            user.getAvatarUrl(),
            user.getBio(),
            iso(user.getCreatedAt()));
    }

    /* ----------------------------------------------------------- category */

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getIcon(),
            category.getColor(),
            category.getDescription());
    }

    /* --------------------------------------------------------------- event */

    /**
     * The card shape. Note that `organizer` and `category` are read through
     * `safe()`, which tolerates the caller not having fetched them — but the
     * repository queries for card lists always join-fetch both, so in practice
     * no extra SELECT is emitted.
     */
    public EventCardResponse toCard(Event event) {
        return new EventCardResponse(
            event.getId(),
            event.getTitle(),
            event.getSlug(),
            event.getShortDescription(),
            event.getCoverImageUrl(),
            safe(() -> toCategoryResponse(event.getCategory())),
            safe(() -> new EventCardResponse.OrganizerSummary(
                event.getOrganizer().getId(),
                event.getOrganizer().getName(),
                event.getOrganizer().getAvatarUrl())),
            iso(event.getStartAt()),
            iso(event.getEndAt()),
            event.getVenueName(),
            event.getCity(),
            event.getCountry(),
            event.getPrice() == null ? 0.0 : event.getPrice(),
            event.getStatus(),
            event.getCapacity(),
            event.getAvailableSeats(),
            event.getRegistrationCount(),
            event.isFeatured(),
            event.getTags() == null ? List.of() : List.copyOf(event.getTags()));
    }

    /** Event answers for `/dashboard/organizer` list of upcoming events. */
    public List<EventCardResponse> toCards(List<Event> events) {
        return events.stream().map(this::toCard).toList();
    }

    public EventDetailResponse toDetail(Event event) {
        return new EventDetailResponse(
            event.getId(),
            event.getTitle(),
            event.getSlug(),
            event.getShortDescription(),
            event.getDescription(),
            event.getCoverImageUrl(),
            safe(() -> toCategoryResponse(event.getCategory())),
            safe(() -> new EventCardResponse.OrganizerSummary(
                event.getOrganizer().getId(),
                event.getOrganizer().getName(),
                event.getOrganizer().getAvatarUrl())),
            iso(event.getStartAt()),
            iso(event.getEndAt()),
            event.getVenueName(),
            event.getAddressLine(),
            event.getCity(),
            event.getCountry(),
            event.getLatitude(),
            event.getLongitude(),
            event.getPrice() == null ? 0.0 : event.getPrice(),
            event.getStatus(),
            event.getCapacity(),
            event.getAvailableSeats(),
            event.getRegistrationCount(),
            event.isFeatured(),
            event.getTags() == null ? List.of() : List.copyOf(event.getTags()),
            event.getTickets() == null ? List.of() : event.getTickets().stream()
                .map(this::toTier).sorted(Comparator.comparing(TicketTierResponse::price,
                        Comparator.nullsLast(Comparator.naturalOrder()))).toList(),
            event.getSpeakers() == null
                ? List.of()
                : event.getSpeakers().stream()
                    .map(s -> new EventDetailResponse.SpeakerResponse(
                        (long) event.getSpeakers().indexOf(s), s.getName(), s.getTitle(), s.getCompany(), s.getPhotoUrl()))
                    .toList(),
            event.getAgenda() == null
                ? List.of()
                : event.getAgenda().stream()
                    .map(a -> new EventDetailResponse.AgendaItemResponse(
                        null, iso(a.getStartsAt()), a.getTitle(), a.getDescription(), a.getSpeaker()))
                    .toList(),
            event.getRating(),
            event.getReviewCount(),
            iso(event.getCreatedAt()));
    }

    public TicketTierResponse toTier(TicketTier tier) {
        return new TicketTierResponse(
            tier.getId(),
            tier.getName(),
            tier.getDescription(),
            tier.getPrice(),
            tier.getQuantityTotal(),
            tier.getQuantitySold(),
            tier.getAvailable(),
            tier.getMaxPerOrder(),
            tier.isSoldOut());
    }

    /* -------------------------------------------------------- registration */

    public RegistrationResponse toRegistration(Registration registration, List<Ticket> tickets) {
        return new RegistrationResponse(
            registration.getId(),
            registration.getBookingRef(),
            toCard(registration.getEvent()),
            registration.getQuantity(),
            registration.getTotalAmount(),
            registration.getStatus(),
            iso(registration.getCreatedAt()),
            tickets.stream().map(this::toTicket).toList());
    }

    public TicketResponse toTicket(Ticket ticket) {
        return new TicketResponse(
            ticket.getId(),
            ticket.getTicketNumber(),
            ticket.getQrPayload(),
            safe(() -> ticket.getEvent().getTitle()),
            safe(() -> iso(ticket.getEvent().getStartAt())),
            safe(() -> ticket.getEvent().getVenueName()),
            safe(() -> ticket.getEvent().getCity()),
            ticket.getHolderName(),
            ticket.getSeatLabel(),
            ticket.getTierName(),
            ticket.getPrice(),
            ticket.getStatus(),
            iso(ticket.getIssuedAt()),
            iso(ticket.getCheckedInAt()));
    }

    public RegistrationResponse toRegistration(Registration registration) {
        return toRegistration(registration,
            registration.getTickets() == null ? List.of() : registration.getTickets());
    }

    /* --------------------------------------------------------------- action */

    public EventActionResponse toAction(EventAction action) {
        return new EventActionResponse(
            action.getId(),
            safe(() -> action.getEvent().getId()),
            action.getEventTitle(),
            action.getActionType().name(),
            action.getDescription(),
            new EventActionResponse.ActorSummary(
                safe(() -> action.getActor().getId()),
                safe(() -> action.getActor().getName())),
            action.isUndone(),
            action.getUndoReason(),
            (int) Math.min(action.getSeq(), Integer.MAX_VALUE),
            iso(action.getCreatedAt()));
    }

    public List<EventActionResponse> toActions(List<EventAction> actions) {
        return actions.stream().map(this::toAction).toList();
    }

    /* --------------------------------------------------------- dashboard */

    public RecentRegistration toRecentRegistration(Registration registration) {
        return new RecentRegistration(
            registration.getId(),
            registration.getAttendeeName(),
            safe(() -> registration.getEvent().getTitle()),
            registration.getTotalAmount(),
            iso(registration.getCreatedAt()));
    }

    public OrganizerDashboardResponse.TopEvent toTopEvent(Event event, long registrations, double revenue) {
        return new OrganizerDashboardResponse.TopEvent(toCard(event), registrations, revenue);
    }

    /* -------------------------------------------------------------- helpers */

    /**
     * Tolerate an uninitialised lazy proxy instead of blowing up mid-mapping.
     * Returning `null` is the correct behaviour here: the field is optional in
     * every consumer, and a fetch join on the hot path makes this unreachable.
     */
    private <T> T safe(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** ISO-8601, UTC — exactly what the TypeScript client parses. */
    private String iso(java.time.Instant instant) {
        if (instant == null) return null;
        // LocalDate(UTC) keeps the display deterministic regardless of host TZ.
        return java.time.ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .replace("Z", "+00:00");
    }

    /** Fall back to UTC for any zone-less timestamp in an embedded object. */
    private java.time.Instant utc(java.time.Instant instant) {
        return instant == null
            ? null
            : java.time.ZonedDateTime.ofInstant(instant, TimeZone.getDefault().toZoneId())
                .toInstant();
    }
}
