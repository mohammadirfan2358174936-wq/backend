package com.example.eventmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Event taxonomy — Music, Technology, Business, Sports, ... */
@Entity
@Table(
    name = "categories",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_categories_slug", columnNames = "slug")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @NotBlank
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @NotBlank
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    /** Lucide icon name — resolved on the client, stored as text here. */
    @Column(name = "icon", length = 60)
    private String icon;

    /** Accent hex used for the category gradient chip. */
    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "description", length = 400)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
