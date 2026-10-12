package com.example.eventmanagement.dto.response;

import com.example.eventmanagement.entity.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** The digital ticket shown on the confirmation screen and the ticket page. */
@Schema(name = "TicketResponse")
public record TicketResponse(
    Long id,
    String ticketNumber,
    String qrPayload,
    String eventTitle,
    String eventStartAt,
    String venueName,
    String city,
    String holderName,
    String seatLabel,
    String tierName,
    Double price,
    TicketStatus status,
    String issuedAt,
    String checkedInAt
) {}
