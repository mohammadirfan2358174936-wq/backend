package com.example.eventmanagement.dto.response;

import com.example.eventmanagement.entity.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Booking confirmation payload. */
@Schema(name = "RegistrationResponse")
public record RegistrationResponse(
    Long id,
    String bookingRef,
    EventCardResponse event,
    Integer quantity,
    Double totalAmount,
    RegistrationStatus status,
    String createdAt,
    List<TicketResponse> tickets
) {}
