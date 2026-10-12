package com.example.eventmanagement.dto.request;

import com.example.eventmanagement.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Create-event payload — step 4 of the event creation wizard.
 *
 * Validation is expressed here rather than in the controller so the same rules
 * apply regardless of entry point (REST, seed data, tests). Tickets are
 * optional on create: an organiser can save a draft and add tiers later.
 */
@Schema(name = "CreateEventRequest", description = "Payload to create a new event")
public record CreateEventRequest(

    @Schema(description = "Event title", example = "Tech Conference 2026", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Event title is required")
    @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    String title,

    @Schema(description = "Long description (markdown allowed)", example = "Three days of deep-tech talks...")
    @Size(max = 8000, message = "Description cannot exceed 8000 characters")
    String description,

    @Schema(description = "Short teaser shown on cards", example = "Three days of AI, cloud and React.")
    @Size(max = 400, message = "Short description cannot exceed 400 characters")
    String shortDescription,

    @Schema(description = "Cover image URL (WebP/AVIF preferred)")
    String coverImageUrl,

    @Schema(description = "Category id", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "Category is required")
    Long categoryId,

    @Schema(description = "Start instant (ISO-8601)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Start date is required")
    Instant startAt,

    @Schema(description = "End instant (ISO-8601)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "End date is required")
    Instant endAt,

    @Schema(description = "Venue name", requiredMode = Schema.RequiredMode.REQUIRED, example = "HITEX Exhibition Center")
    @NotBlank(message = "Venue name is required")
    @Size(max = 200, message = "Venue name cannot exceed 200 characters")
    String venueName,

    @Schema(description = "Street address", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Address is required")
    @Size(max = 400)
    String addressLine,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Hyderabad")
    @NotBlank(message = "City is required")
    @Size(max = 120)
    String city,

    @Schema(example = "India")
    @NotBlank(message = "Country is required")
    @Size(max = 120)
    String country,

    @Schema(description = "Latitude")
    Double latitude,

    @Schema(description = "Longitude")
    Double longitude,

    @Schema(description = "Total venue capacity", requiredMode = Schema.RequiredMode.REQUIRED, example = "500")
    @NotNull(message = "Capacity is required")
    @PositiveOrZero(message = "Capacity cannot be negative")
    Integer capacity,

    @Schema(description = "Starting price; 0 for free events", example = "999")
    @DecimalMin(value = "0.0", message = "Price cannot be negative")
    Double price,

    @Schema(description = "Initial status. Defaults to DRAFT.")
    EventStatus status,

    @Schema(description = "Feature on the landing page")
    Boolean featured,

    @Schema(description = "Free-form tags")
    List<String> tags,

    @Schema(description = "Ticket tiers")
    @Valid
    List<TicketTierRequest> tickets

) {

    /**
     * One ticket tier. `quantityTotal` is validated against the event capacity
     * by `EventService`, which owns the cross-field rule.
     */
    @Schema(name = "TicketTierRequest")
    public record TicketTierRequest(

        @Schema(description = "Tier name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Early Bird")
        @NotBlank(message = "Ticket tier name is required")
        @Size(max = 80)
        String name,

        @Schema(description = "Tier description")
        @Size(max = 400)
        String description,

        @Schema(example = "999")
        @DecimalMin(value = "0.0", message = "Price cannot be negative")
        Double price,

        @Schema(example = "200")
        @NotNull(message = "Quantity is required")
        @PositiveOrZero(message = "Quantity cannot be negative")
        Integer quantityTotal,

        @Schema(example = "10")
        @PositiveOrZero(message = "Max per order cannot be negative")
        Integer maxPerOrder
    ) {}
}
