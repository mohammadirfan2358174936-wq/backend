package com.example.eventmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * Payment record for a registration.
 *
 * One-to-one with the registration: a booking is paid exactly once. The
 * `transactionRef` is the idempotency key handed to the gateway, so a retried
 * release cannot create a double charge.
 */
@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_registration", columnNames = "registration_id"),
        @UniqueConstraint(name = "uk_payments_transaction_ref", columnNames = "transaction_ref")
    },
    indexes = {
        @Index(name = "idx_payments_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payments_registration"))
    @ToString.Exclude
    private Registration registration;

    @PositiveOrZero
    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "INR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Idempotency key for the payment gateway. */
    @Column(name = "transaction_ref", nullable = false, length = 64)
    private String transactionRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
        this.paidAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }
}
