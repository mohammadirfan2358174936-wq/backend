package com.example.eventmanagement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Query parameters for event discovery.
 *
 * `@DateTimeFormat(iso = ISO.DATE_TIME)` makes `from=2026-10-01T00:00:00Z`
 * bind cleanly; the controller also accepts epoch millis. Every field is
 * optional — the repository query uses `:param is null or ...` guards, so no
 * dynamic SQL is ever built.
 */
@Schema(name = "EventQueryParams")
public record EventQueryParams(

    @Schema(description = "Zero-based page number", example = "0")
    Integer page,

    @Schema(description = "Page size (max 100)", example = "12")
    Integer size,

    @Schema(description = "Sort: field,direction e.g. startAt,desc", example = "startAt,asc")
    String sort,

    @Schema(description = "Category slug", example = "technology")
    String category,

    @Schema(description = "Status override (organiser/admin views)", example = "DRAFT")
    com.example.eventmanagement.entity.EventStatus status,

    @Schema(description = "City filter (case-insensitive contains)", example = "Hyderabad")
    String city,

    @Schema(description = "Free-text search on title and description", example = "tech")
    String q,

    @Schema(description = "Earliest start instant")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant from,

    @Schema(description = "Latest start instant")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant to,

    @Schema(description = "Minimum price", example = "0")
    Double minPrice,

    @Schema(description = "Maximum price", example = "5000")
    Double maxPrice,

    @Schema(description = "Only featured events", example = "true")
    Boolean featured

) {

    /** Normalised page number, defaulting to 0. */
    public int pageOrZero() {
        return page == null || page < 0 ? 0 : page;
    }

    /** Page size clamped into [1, 100]; defaults to 12. */
    public int sizeOrDefault(int fallback, int max) {
        if (size == null || size < 1) return fallback;
        return Math.min(size, max);
    }

    /** Split `field,dir` into its two halves, defaulting to `startAt,asc`. */
    public String sortField() {
        if (sort == null || sort.isBlank()) return "startAt";
        String field = sort.split(",")[0].trim();
        return field.isBlank() ? "startAt" : field;
    }

    public boolean sortAscending() {
        if (sort == null || !sort.contains(",")) return true;
        return !"desc".equalsIgnoreCase(sort.split(",")[1].trim());
    }
}
