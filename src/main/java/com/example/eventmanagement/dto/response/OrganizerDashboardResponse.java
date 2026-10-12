package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** `GET /dashboard/organizer` */
@Schema(name = "OrganizerDashboardResponse")
public record OrganizerDashboardResponse(
    StatTile stats,
    List<SeriesPoint> series,
    List<CategorySlice> categorySplit,
    List<EventCardResponse> upcoming,
    List<EventActionResponse> recentActivity,
    List<TopEvent> topEvents
) {

    @Schema(name = "OrganizerStats")
    public record StatTile(
        Double revenue,
        Long registrations,
        Long events,
        Long attendees,
        Double conversionRate
    ) {}

    @Schema(name = "SeriesPoint")
    public record SeriesPoint(
        String month,
        Double revenue,
        Long registrations
    ) {}

    @Schema(name = "CategorySlice")
    public record CategorySlice(
        String name,
        Long value
    ) {}

    @Schema(name = "TopEvent")
    public record TopEvent(
        EventCardResponse event,
        Long registrations,
        Double revenue
    ) {}
}
