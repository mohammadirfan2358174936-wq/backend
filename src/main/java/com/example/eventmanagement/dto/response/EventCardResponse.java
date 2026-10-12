package com.example.eventmanagement.dto.response;

import com.example.eventmanagement.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The card-grid payload.
 *
 * Deliberately flat and narrow: it is returned by every list endpoint and
 * is the only shape the discovery grid, dashboards and "my events" ever need.
 */
@Schema(name = "EventCardResponse")
public record EventCardResponse(
    Long id,
    String title,
    String slug,
    String shortDescription,
    String coverImageUrl,
    CategoryResponse category,
    OrganizerSummary organizer,
    String startAt,
    String endAt,
    String venueName,
    String city,
    String country,
    Double price,
    EventStatus status,
    Integer capacity,
    Integer availableSeats,
    Integer registrationCount,
    boolean featured,
    java.util.List<String> tags
) {

    @Schema(name = "OrganizerSummary")
    public record OrganizerSummary(
        Long id,
        String name,
        String avatarUrl
    ) {}
}
