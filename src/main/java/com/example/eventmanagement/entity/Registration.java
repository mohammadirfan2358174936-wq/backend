package com.example.eventmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A booking: one user, one event, one ticket tier, N seats.
 *
 * The database enforces the business rule "a user cannot register for the same
 * event twice" through {@code UNIQUE(user_id, event_id)} — a constraint, not an
 * application check, so a race between two simultaneous requests is rejected
 * by the database rather than slipping through.
 */
@Entity
@Table(
    name = "registrations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_registrations_user_event", columnNames = { "user_id", "event_id" }),
        @UniqueConstraint(name = "uk_registrations_booking_ref", columnNames = "booking_ref")
    },
    indexes = {
        @Index(name = "idx_registrations_user", columnList = "user_id"),
        @Index(name = "idx_registrations_event", columnList = "event_id"),
        @Index(name = "idx_registrations_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration extends BaseEntity {

    /** Human-readable confirmation code, e.g. `EVT-7X4KQ9`. */
    @NotBlank
    @Column(name = "booking_ref", nullable = false, length = 24)
    private String bookingRef;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registrations_event"))
    @ToString.Exclude
    private Event event;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registrations_user"))
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_tier_id", foreignKey = @ForeignKey(name = "fk_registrations_ticket_tier"))
    @ToString.Exclude
    private TicketTier ticketTier;

    @Positive
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @PositiveOrZero
    @Column(name = "unit_price", nullable = false)
    @Builder.Default
    private Double unitPrice = 0.0;

    @PositiveOrZero
    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private Double totalAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @NotBlank
    @Column(name = "attendee_name", nullable = false, length = 160)
    private String attendeeName;

    @Email
    @NotBlank
    @Column(name = "attendee_email", nullable = false, length = 180)
    private String attendeeEmail;

    @Column(name = "attendee_phone", length = 32)
    private String attendeePhone;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @OneToOne(mappedBy = "registration", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Payment payment;

    @OneToMany(mappedBy = "registration", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @Builder.Default
    private java.util.List<Ticket> tickets = new java.util.ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void markConfirmed() {
        this.status = RegistrationStatus.CONFIRMED;
    }

    public void markCancelled() {
        this.status = RegistrationStatus.CANCELLED;
    }

    public int seatsHeld() {
        return status.occupiesSeat() ? quantity : 0;
    }
}
