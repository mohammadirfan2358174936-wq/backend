package com.example.eventmanagement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Update-event payload.
 *
 * `capacity` is the only field that requires special care: shrinking capacity
 * below the number of seats already sold must be rejected, otherwise
 * `availableSeats` would have to go negative. That rule lives in
 * `EventService#update`.
 */
@Schema(name = "UpdateEventRequest")
public record UpdateEventRequest(

    @Schema(description = "Event title")
    @Size(min = 5, max = 200)
    String title,

    @Schema(description = "Long description")
    @Size(max = 8000)
    String description,

    @Schema(description = "Short teaser")
    @Size(max = 400)
    String shortDescription,

    @Schema(description = "Cover image URL")
    String coverImageUrl,

    @Schema(description = "Category id")
    Long categoryId,

    Instant startAt,

    Instant endAt,

    String venueName,

    String addressLine,

    String city,

    String country,

    Double latitude,

    Double longitude,

    @DecimalMin(value = "0.0", message = "Capacity cannot be negative")
    Integer capacity,

    @DecimalMin(value = "0.0", message = "Price cannot be negative")
    Double price,

    Boolean featured,

    List<String> tags,

    @Valid
    List<CreateEventRequest.TicketTierRequest> tickets

) {

    @Schema(name = "TicketTierRequest")
    public record TicketTierRequest(
        @Schema(description = "Tier id — omit to create a new tier")
        Long id,

        String name,
        String description,
        @DecimalMin(value = "0.0") Double price,
        @PositiveOrZero Integer quantityTotal,
        @PositiveOrZero Integer maxPerOrder,
        Boolean active
    ) {}
}
