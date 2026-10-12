package com.example.eventmanagement.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AGGREGATE ROOT — the event.
 *
 * Design notes
 * ------------
 * 1. **Optimistic locking.** `BaseEntity` declares `@Version`, so two organisers
 *    editing the same event cannot silently overwrite each other — Hibernate
 *    raises `OptimisticLockingFailureException` and we surface `409 CONFLICT`.
 *
 * 2. **Laziness by default.** `category`, `organizer`, `tickets`, `registrations`
 *    are all `LAZY`. Nothing is ever serialised; the service layer joins-fetches
 *    exactly what a given endpoint needs (list → card shape, detail → full shape).
 *
 * 3. **`availableSeats` is derived, and defensively maintained.** It is
 *    decremented by `RegistrationService#register` *inside* a pessimistic-lock
 *    transaction. `@SQLRestriction` keeps soft-deleted rows out of every query.
 *
 * 4. **`version` is incremented on every capacity mutation** so undo can detect
 *    a conflict (concurrent change since the snapshot was taken).
 */
@Entity
@Table(
    name = "events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_events_slug", columnNames = "slug")
    },
    indexes = {
        @Index(name = "idx_events_status_start", columnList = "status, start_at"),
        @Index(name = "idx_events_category", columnList = "category_id"),
        @Index(name = "idx_events_city", columnList = "city"),
        @Index(name = "idx_events_organizer", columnList = "organizer_id"),
        @Index(name = "idx_events_featured", columnList = "featured, start_at"),
        @Index(name = "idx_events_slug", columnList = "slug", unique = true)
    }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends BaseEntity {

    /* ------------------------------------------------------------- content */

    @NotBlank
    @Size(min = 3, max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** URL-safe identifier; unique and indexed for fast lookups. */
    @Column(name = "slug", nullable = false, length = 220)
    private String slug;

    @Column(name = "description", length = 8000)
    private String description;

    @Size(max = 400)
    @Column(name = "short_description", length = 400)
    private String shortDescription;

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    /** Free-form tags, persisted as a native text[] on PostgreSQL. */
    @ElementCollection
    @CollectionTable(
        name = "event_tags",
        joinColumns = @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_event_tags_event")),
        indexes = @Index(name = "idx_event_tags_event", columnList = "event_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "tag", length = 40)
    @org.hibernate.annotations.BatchSize(size = 64)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "event_speakers",
        joinColumns = @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_event_speakers_event")),
        indexes = @Index(name = "idx_event_speakers_event", columnList = "event_id")
    )
    @OrderColumn(name = "position")
    @org.hibernate.annotations.BatchSize(size = 32)
    @Builder.Default
    private List<Speaker> speakers = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "event_agenda",
        joinColumns = @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_event_agenda_event")),
        indexes = @Index(name = "idx_event_agenda_event", columnList = "event_id")
    )
    @OrderColumn(name = "position")
    @org.hibernate.annotations.BatchSize(size = 32)
    @Builder.Default
    private List<AgendaItem> agenda = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "event_reviews",
        joinColumns = @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_event_reviews_event")),
        indexes = @Index(name = "idx_event_reviews_event", columnList = "event_id")
    )
    @OrderColumn(name = "position")
    @org.hibernate.annotations.BatchSize(size = 16)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    /* --------------------------------------------------------- relationships */

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_category"))
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Category category;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_organizer"))
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private User organizer;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @org.hibernate.annotations.BatchSize(size = 32)
    @ToString.Exclude
    @Builder.Default
    private List<TicketTier> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    @ToString.Exclude
    @Builder.Default
    private List<Registration> registrations = new ArrayList<>();

    /* ----------------------------------------------------------- time & place */

    @NotNull
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @NotBlank
    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @NotBlank
    @Column(name = "address_line", nullable = false, length = 400)
    private String addressLine;

    @NotBlank
    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @NotBlank
    @Column(name = "country", nullable = false, length = 120)
    @Builder.Default
    private String country = "India";

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /* ------------------------------------------------------- tickets & seats */

    /** Total venue capacity. */
    @NotNull
    @PositiveOrZero
    @Column(name = "capacity", nullable = false)
    @Builder.Default
    private Integer capacity = 0;

    /**
     * Seats still bookable. Maintained transactionally under the pessimistic
     * lock in `RegistrationService`.
     */
    @NotNull
    @Column(name = "available_seats", nullable = false)
    @Builder.Default
    private Integer availableSeats = 0;

    /** Denormalised booking counter — avoids a COUNT(*) on every card. */
    @Column(name = "registration_count", nullable = false)
    @Builder.Default
    private Integer registrationCount = 0;

    /** Starting price across all tiers; 0 means free. */
    @Column(name = "price", nullable = false)
    @Builder.Default
    private Double price = 0.0;

    /* ---------------------------------------------------------------- status */

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "featured", nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "cancelled", nullable = false)
    @Builder.Default
    private boolean cancelled = false;

    /** Soft delete — never remove an event that holds bookings. */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "rating", nullable = false)
    @Builder.Default
    private Double rating = 0.0;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /* ------------------------------------------------------------ behaviour */

    public boolean isPubliclyVisible() {
        return !deleted && status.isPubliclyVisible();
    }

    public boolean isPublishable() {
        return status == EventStatus.DRAFT && capacity > 0 && availableSeats > 0;
    }

    /**
     * Decrement capacity. Callers MUST hold the pessimistic write lock on this
     * row for the duration of the transaction — see the service layer.
     */
    public void decrementSeats(int requested) {
        if (requested <= 0) {
            throw new IllegalArgumentException("requested seats must be positive");
        }
        if (availableSeats < requested) {
            throw new IllegalStateException("insufficient seats");
        }
        this.availableSeats = this.availableSeats - requested;
        this.registrationCount = this.registrationCount + requested;
    }

    public void incrementSeats(int released) {
        if (released <= 0) return;
        this.availableSeats = Math.min(capacity, this.availableSeats + released);
        this.registrationCount = Math.max(0, this.registrationCount - released);
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("only drafts can be published");
        }
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.cancelled = false;
        this.cancelledAt = null;
    }

    public void cancel() {
        this.status = EventStatus.CANCELLED;
        this.cancelled = true;
        this.cancelledAt = Instant.now();
    }

    public void markCompleted() {
        if (status == EventStatus.PUBLISHED) {
            this.status = EventStatus.COMPLETED;
        }
    }

    public void softDelete() {
        this.deleted = true;
        if (status == EventStatus.PUBLISHED) {
            this.status = EventStatus.DRAFT;
        }
    }

    public void addTicket(TicketTier tier) {
        if (tickets == null) tickets = new ArrayList<>();
        tier.setEvent(this);
        tickets.add(tier);
    }

    /** Lightweight copy used when snapshotting state for `undo`. */
    public EventSnapshot snapshot() {
        return new EventSnapshot(
            title, status, capacity, availableSeats, price,
            venueName, addressLine, city, country, startAt, endAt,
            description, shortDescription, coverImageUrl, featured,
            new ArrayList<>(tags));
    }

    /** Point-in-time value of the mutable fields of this event. */
    public record EventSnapshot(
        String title,
        EventStatus status,
        int capacity,
        int availableSeats,
        double price,
        String venueName,
        String addressLine,
        String city,
        String country,
        Instant startAt,
        Instant endAt,
        String description,
        String shortDescription,
        String coverImageUrl,
        boolean featured,
        List<String> tags
    ) {}
}
