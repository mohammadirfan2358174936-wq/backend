package com.example.eventmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;

/**
 * Shared identity for every entity.
 *
 * `GenerationType.IDENTITY` lets PostgreSQL assign the key via `BIGSERIAL`,
 * which keeps inserts single-statement (no extra round-trip to a sequence).
 *
 * `@Version` is declared here so every aggregate is protected by optimistic
 * locking. `Event` in particular relies on it so a stale organiser edit can
 * never silently clobber a concurrent update.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        BaseEntity other = (BaseEntity) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "%s(id=%s)".formatted(Hibernate.getClass(this).getSimpleName(), id);
    }
}
