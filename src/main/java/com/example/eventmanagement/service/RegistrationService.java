package com.example.eventmanagement.service;

import com.example.eventmanagement.dto.request.RegisterRequest;
import com.example.eventmanagement.dto.response.RegistrationResponse;
import com.example.eventmanagement.dto.response.TicketResponse;
import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventStatus;
import com.example.eventmanagement.entity.Payment;
import com.example.eventmanagement.entity.PaymentMethod;
import com.example.eventmanagement.entity.PaymentStatus;
import com.example.eventmanagement.entity.Registration;
import com.example.eventmanagement.entity.RegistrationStatus;
import com.example.eventmanagement.entity.Ticket;
import com.example.eventmanagement.entity.TicketStatus;
import com.example.eventmanagement.entity.TicketTier;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.exception.DuplicateRegistrationException;
import com.example.eventmanagement.exception.EventFullException;
import com.example.eventmanagement.exception.EventNotFoundException;
import com.example.eventmanagement.exception.InvalidEventStateException;
import com.example.eventmanagement.exception.RegistrationNotFoundException;
import com.example.eventmanagement.exception.TicketNotFoundException;
import com.example.eventmanagement.mapper.DtoMapper;
import com.example.eventmanagement.repository.EventRepository;
import com.example.eventmanagement.repository.PaymentRepository;
import com.example.eventmanagement.repository.RegistrationRepository;
import com.example.eventmanagement.repository.TicketRepository;
import com.example.eventmanagement.util.ApiResponse;
import com.example.eventmanagement.util.AppUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * REGISTRATION SERVICE
 * ====================
 * Booking, cancellation, ticketing and payment.
 *
 * ## The overbooking problem, and why this class looks the way it does
 *
 * A naive implementation is:
 *
 * ```java
 * if (event.getAvailableSeats() > 0) {          // ← read
 *     event.setAvailableSeats(event.getAvailableSeats() - 1);   // ← write
 * }
 * ```
 *
 * This is a **read-then-write race**. Two requests that both read
 * `availableSeats == 1` will both pass the check and both decrement, leaving the
 * count at -1 and selling a seat that does not exist. The window is a few
 * milliseconds wide — exactly wide enough to break under load.
 *
 * The defence here is layered:
 *
 * 1. **Pessimistic lock.** `findByIdForUpdate` emits `SELECT ... FOR UPDATE`,
 *    so only one transaction can hold the event row at a time. Every other
 *    concurrent registration blocks until the first commits.
 * 2. **Re-check inside the transaction.** The capacity check happens *after*
 *    acquiring the lock, against the authoritative row.
 * 3. **Database constraint.** `UNIQUE(user_id, event_id)` makes a duplicate
 *    booking physically impossible; a lost race surfaces as
 *    `DataIntegrityViolationException` and is translated into a 409.
 * 4. **Optimistic locking.** `@Version` on `Event` catches organizer-side edits
 *    that would otherwise silently clobber a concurrent update.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final Set<RegistrationStatus> SEAT_OCCUPYING =
        EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.PENDING);

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final EventActionStack actionStack;
    private final DtoMapper mapper;

    /* ------------------------------------------------------------- register */

    /**
     * Book seats on an event.
     *
     * The whole body runs under a single transaction, with the event row locked
     * for its duration. Nothing between the lock acquisition and the commit may
     * read a stale seat count.
     */
    @Transactional
    public RegistrationResponse register(Long eventId, RegisterRequest request, User attendee) {
        // ── 1. Take the write lock. Concurrent callers queue here. ─────────
        Event event = eventRepository.findByIdForUpdate(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.PUBLISHED || event.isCancelled()) {
            throw new InvalidEventStateException("This event is not accepting registrations.");
        }

        // ── 2. duplicate guard (fast, clean 409) ─────────────────────────
        boolean alreadyBooked = registrationRepository
            .findByUserIdAndEventId(attendee.getId(), eventId)
            .map(r -> SEAT_OCCUPYING.contains(r.getStatus()))
            .orElse(false);

        if (alreadyBooked) {
            throw new DuplicateRegistrationException(event.getTitle());
        }

        // ── 3. resolve the tier ─────────────────────────────────────────
        TicketTier tier = event.getTickets().stream()
            .filter(t -> t.getId().equals(request.ticketTierId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Ticket tier %d does not belong to this event.".formatted(request.ticketTierId())));

        int quantity = request.quantity();
        int available = tier.getAvailable();

        // ── 4. THE capacity re-check, inside the transaction ─────────────
        //    A guarded tier can never sell out from under us now.
        if (available < quantity) {
            log.info("Booking refused: event {} tier {} has {} seat(s), {} requested",
                eventId, tier.getName(), available, quantity);
            throw new EventFullException(event.getTitle(), available, quantity);
        }

        if (event.getAvailableSeats() < quantity) {
            throw new EventFullException(event.getTitle(), event.getAvailableSeats(), quantity);
        }

        // ── 5. mutate, inside the transaction ───────────────────────────
        double unitPrice = tier.getPrice() == null ? 0.0 : tier.getPrice();
        double total = unitPrice * quantity;

        tier.reserve(quantity);
        event.decrementSeats(quantity);

        String bookingRef = AppUtils.bookingReference();

        Registration registration = Registration.builder()
            .bookingRef(bookingRef)
            .event(event)
            .user(attendee)
            .ticketTier(tier)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .totalAmount(total)
            .status(RegistrationStatus.PENDING)
            .attendeeName(request.attendee().fullName().trim())
            .attendeeEmail(request.attendee().email().trim().toLowerCase())
            .attendeePhone(request.attendee().phone())
            .notes(request.attendee().notes())
            .couponCode(request.couponCode())
            .build();

        Registration saved = registrationRepository.saveAndFlush(registration);

        try {
            saved.markConfirmed();
            List<Ticket> tickets = issueTickets(saved, event, tier, attendee, request, quantity);
            settlePayment(saved, request.paymentMethod(), total);

            Registration persisted = registrationRepository.saveAndFlush(saved);
            eventRepository.save(event);

            log.info("Booking {} created for user {} on event {} ({} seat(s))",
                bookingRef, attendee.getId(), eventId, quantity);

            return mapper.toRegistration(persisted, tickets);
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Data integrity violation during booking for user {} on event {}: {}", attendee.getId(), eventId, duplicate.getMessage());
            throw new DuplicateRegistrationException(event.getTitle());
        }
    }

    /* -------------------------------------------------------------- cancel */

    /**
     * Cancel a booking and return the seats to the pool.
     *
     * Runs in its own transaction with the event locked, so a cancellation and a
     * concurrent booking cannot interleave.
     */
    @Transactional
    public void cancel(Long eventId, User attendee) {
        Event event = eventRepository.findByIdForUpdate(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        Registration registration = registrationRepository
            .findActive(attendee.getId(), eventId, SEAT_OCCUPYING)
            .orElseThrow(() -> new RegistrationNotFoundException(eventId, attendee.getId()));

        int released = registration.seatsHeld();

        registration.markCancelled();
        registration.getTickets().forEach(ticket -> {
            ticket.refund();
            ticketRepository.save(ticket);
        });

        event.incrementSeats(released);
        if (registration.getTicketTier() != null) {
            registration.getTicketTier().release(released);
        }

        eventRepository.save(event);
        registrationRepository.save(registration);

        log.info("Booking {} cancelled — {} seat(s) returned to event {}",
            registration.getBookingRef(), released, eventId);
    }

    /* ----------------------------------------------------------- my events */

    @Transactional(readOnly = true)
    public ApiResponse.PagedBody<com.example.eventmanagement.dto.response.EventCardResponse> myEvents(
        User attendee, int page, int size) {

        Page<Registration> result = registrationRepository.findMyEventsPaged(
            attendee.getId(), EnumSet.of(RegistrationStatus.CONFIRMED),
            org.springframework.data.domain.PageRequest.of(page, size));

        return ApiResponse.paged(result,
            r -> mapper.toCard(r.getEvent()),
            "You are attending %d event(s)".formatted(result.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ApiResponse.PagedBody<RegistrationResponse> myRegistrations(User attendee, int page, int size) {
        var result = registrationRepository.findByUserPaged(
            attendee.getId(), org.springframework.data.domain.PageRequest.of(page, size));

        return ApiResponse.paged(result, mapper::toRegistration,
            "Found %d booking(s)".formatted(result.getTotalElements()));
    }

    /* -------------------------------------------------------------- tickets */

    @Transactional(readOnly = true)
    public ApiResponse.PagedBody<TicketResponse> myTickets(User attendee, int page, int size) {
        var result = ticketRepository.findByUserPaged(
            attendee.getId(), org.springframework.data.domain.PageRequest.of(page, size));

        return ApiResponse.paged(result, mapper::toTicket,
            "Found %d ticket(s)".formatted(result.getTotalElements()));
    }

    /**
     * Digital ticket by its printed number.
     *
     * The lookup is a single index seek on `uk_tickets_number` — no joins,
     * because the display fields are denormalised onto the row.
     */
    @Transactional(readOnly = true)
    public TicketResponse ticket(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
            .orElseThrow(() -> new TicketNotFoundException(ticketNumber));
        return mapper.toTicket(ticket);
    }

    /** Door check-in. Idempotent, returns the holder for the scanner UI. */
    @Transactional
    public TicketResponse validate(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
            .orElseThrow(() -> new TicketNotFoundException(ticketNumber));
        ticket.checkIn();
        return mapper.toTicket(ticketRepository.save(ticket));
    }

    /* -------------------------------------------------------------- payment */

    /**
     * Simulated payment capture.
     *
     * A real integration would call the gateway here; the point of the seam is
     * that the registration row and the payment row are written in the *same*
     * transaction, so the payment can never exist without its booking.
     */
    private void settlePayment(Registration registration, PaymentMethod method, double amount) {
        String reference = AppUtils.transactionReference(registration.getId());

        Payment payment = Payment.builder()
            .registration(registration)
            .amount(amount)
            .method(method == null ? PaymentMethod.CARD : method)
            .status(PaymentStatus.SUCCESS)
            .transactionRef(reference)
            .build();
        payment.markSuccess();
        registration.setPayment(payment);
    }

    /* -------------------------------------------------------------- tickets */

    /**
     * Issue N individual tickets for one booking.
     *
     * Each seat gets its own ticket number and QR payload so a booking can be
     * partially redeemed at the door.
     */
    private List<Ticket> issueTickets(Registration registration, Event event, TicketTier tier,
                                     User attendee, RegisterRequest request, int quantity) {
        List<Ticket> tickets = new java.util.ArrayList<>(quantity);

        for (int i = 0; i < quantity; i += 1) {
            Ticket ticket = Ticket.builder()
                .ticketNumber(AppUtils.ticketNumber(registration.getBookingRef(), i, quantity))
                .registration(registration)
                .event(event)
                .user(attendee)
                .tierName(tier.getName())
                .holderName(request.attendee().fullName().trim())
                .seatLabel("GEN-" + (i + 1))
                .price(tier.getPrice() == null ? 0.0 : tier.getPrice())
                .qrPayload(qrPayload(registration, i))
                .status(TicketStatus.VALID)
                .build();

            registration.getTickets().add(ticket);
            tickets.add(ticket);
        }

        return tickets;
    }

    /**
     * Self-contained QR payload: a scanner needs no network round-trip.
     * Format: `EVENTORA|ticketNumber|eventId|checksum`.
     */
    private String qrPayload(Registration registration, int index) {
        String number = AppUtils.ticketNumber(registration.getBookingRef(), index, registration.getQuantity());
        int checksum = (number.hashCode() & 0x7fffffff) % 100000;
        return "EVENTORA|%s|%d|%05d".formatted(number, registration.getId(), checksum);
    }

    /* ---------------------------------------------------------------- stats */

    public long totalSeatsSold() {
        return registrationRepository.countSeatsByStatus(RegistrationStatus.CONFIRMED);
    }
}
