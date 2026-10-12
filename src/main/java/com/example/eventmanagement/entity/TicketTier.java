package com.example.eventmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A price band for an event (Early Bird, Standard, VIP...).
 *
 * This is an entity — not an embedded value object — because registrations
 * reference it with a real foreign key (`registrations.ticket_tier_id`).
 *
 * `available` and `sold` are maintained transactionally by
 * `RegistrationService#register` under a pessimistic lock on the parent event,
 * so they are a derived cache, never a source of truth.
 */
@jakarta.persistence.Entity
@Table(
    name = "ticket_tiers",
    indexes = {
        @Index(name = "idx_ticket_tiers_event", columnList = "event_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTier extends BaseEntity {

    @NotBlank
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 400)
    private String description;

    @NotNull
    @PositiveOrZero
    @Column(name = "price", nullable = false)
    @Builder.Default
    private Double price = 0.0;

    @Column(name = "quantity_total", nullable = false)
    @Builder.Default
    private Integer quantityTotal = 0;

    @Column(name = "quantity_sold", nullable = false)
    @Builder.Default
    private Integer quantitySold = 0;

    @Column(name = "max_per_order", nullable = false)
    @Builder.Default
    private Integer maxPerOrder = 10;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /* ------------------------------------------------------- relationships */

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_ticket_tiers_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Event event;

    /* ------------------------------------------------------------ behaviour */

    public int getAvailable() {
        return Math.max(0, quantityTotal - quantitySold);
    }

    public boolean hasAvailability(int requested) {
        return active && getAvailable() >= requested;
    }

    /** Seats sold out? */
    public boolean isSoldOut() {
        return !active || getAvailable() <= 0;
    }

    public void reserve(int quantity) {
        this.quantitySold = this.quantitySold + quantity;
    }

    public void release(int quantity) {
        this.quantitySold = Math.max(0, this.quantitySold - quantity);
    }
}
