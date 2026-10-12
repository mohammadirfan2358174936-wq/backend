package com.example.eventmanagement.dto.response;

import com.example.eventmanagement.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Payment receipt attached to a registration. */
@Schema(name = "PaymentResponse")
public record PaymentResponse(
    Long id,
    Double amount,
    String currency,
    String method,
    PaymentStatus status,
    String transactionRef,
    String paidAt
) {}
