package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One ticket tier for the registration wizard. */
@Schema(name = "TicketTierResponse")
public record TicketTierResponse(
    Long id,
    String name,
    String description,
    Double price,
    Integer quantityTotal,
    Integer quantitySold,
    Integer available,
    Integer maxPerOrder,
    boolean sold
) {}
