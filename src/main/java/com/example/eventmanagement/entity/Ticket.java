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

import java.time.Instant;

/**
 * The digital ticket issued for a registration.
 *
 * One registration of quantity N yields N tickets sharing a `ticketNumber`
 * prefix but each carrying its own unique number and QR payload, so a single
 * booking can be partially checked in.
 */
@Entity
@Table(
    name = "tickets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tickets_number", columnNames = "ticket_number")
    },
    indexes = {
        @Index(name = "idx_tickets_event", columnList = "event_id"),
        @Index(name = "idx_tickets_user", columnList = "user_id"),
        @Index(name = "idx_tickets_registration", columnList = "registration_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends BaseEntity {

    /** `EVT-<bookingRef>-<index>` — printed and scanned at the door. */
    @NotBlank
    @Column(name = "ticket_number", nullable = false, length = 40)
    private String ticketNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_registration"))
    @ToString.Exclude
    private Registration registration;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_event"))
    @ToString.Exclude
    private Event event;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_user"))
    @ToString.Exclude
    private User user;

    /** Redundant vendor/label columns let the QR render without any joins. */
    @Column(name = "tier_name", length = 80)
    private String tierName;

    @Column(name = "holder_name", nullable = false, length = 160)
    private String holderName;

    @Column(name = "seat_label", length = 40)
    private String seatLabel;

    @PositiveOrZero
    @Column(name = "price", nullable = false)
    @Builder.Default
    private Double price = 0.0;

    /**
     * Encoded QR payload. Self-contained so a scanner needs no network round
     * trip: `EVENTORA|ticketNumber|eventId|holderHash`.
     */
    @Column(name = "qr_payload", nullable = false, length = 500)
    private String qrPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.VALID;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    /** Idempotent door check-in. */
    public boolean checkIn() {
        if (status.isRedeemable()) {
            this.status = TicketStatus.USED;
            this.checkedInAt = Instant.now();
            return true;
        }
        return false;
    }

    public void refund() {
        this.status = TicketStatus.REFUNDED;
    }
}
