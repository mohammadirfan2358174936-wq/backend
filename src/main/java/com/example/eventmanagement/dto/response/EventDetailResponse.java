package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.eventmanagement.entity.EventStatus;

import java.util.List;

/** Full event detail — one round trip paints the whole event page. */
@Schema(name = "EventDetailResponse")
public record EventDetailResponse(
    Long id,
    String title,
    String slug,
    String shortDescription,
    String description,
    String coverImageUrl,
    CategoryResponse category,
    EventCardResponse.OrganizerSummary organizer,
    String startAt,
    String endAt,
    String venueName,
    String addressLine,
    String city,
    String country,
    Double latitude,
    Double longitude,
    Double price,
    EventStatus status,
    Integer capacity,
    Integer availableSeats,
    Integer registrationCount,
    boolean featured,
    List<String> tags,
    List<TicketTierResponse> tickets,
    List<SpeakerResponse> speakers,
    List<AgendaItemResponse> agenda,
    Double rating,
    Integer reviewCount,
    String createdAt
) {

    @Schema(name = "SpeakerResponse")
    public record SpeakerResponse(
        Long id,
        String name,
        String title,
        String company,
        String photoUrl
    ) {}

    @Schema(name = "AgendaItemResponse")
    public record AgendaItemResponse(
        Long id,
        String startsAt,
        String title,
        String description,
        String speaker
    ) {}
}
