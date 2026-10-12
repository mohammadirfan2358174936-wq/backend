package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One entry of the event action stack (LIFO order). */
@Schema(name = "EventActionResponse")
public record EventActionResponse(
    Long id,
    Long eventId,
    String eventTitle,
    String type,
    String description,
    ActorSummary actor,
    boolean undone,
    String undoReason,
    int sequence,
    String createdAt
) {

    @Schema(name = "ActorSummary")
    public record ActorSummary(
        Long id,
        String name
    ) {}
}
