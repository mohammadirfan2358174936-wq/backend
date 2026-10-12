package com.example.eventmanagement.dto.request;

import com.example.eventmanagement.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Booking payload — the "Select Ticket → Attendee Information → Payment" wizard.
 */
@Schema(name = "RegisterRequest")
public record RegisterRequest(

    @Schema(description = "Ticket tier id", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    @NotNull(message = "Please choose a ticket tier")
    Long ticketTierId,

    @Schema(description = "Number of seats (1–10)", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "You must book at least one seat")
    @Max(value = 10, message = "You cannot book more than 10 seats at once")
    Integer quantity,

    @Schema(description = "Attendee information", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "Attendee information is required")
    AttendeeRequest attendee,

    @Schema(description = "Payment method", requiredMode = Schema.RequiredMode.REQUIRED, example = "UPI")
    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod,

    @Schema(description = "Coupon code")
    @Size(max = 40)
    String couponCode

) {

    @Schema(name = "AttendeeRequest")
    public record AttendeeRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Irfan Ahmed")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 160, message = "Name must be between 2 and 160 characters")
        String fullName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "irfan@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 180)
        String email,

        @Schema(example = "+91 98765 43210")
        @Size(max = 32)
        String phone,

        @Schema(description = "Notes for the organiser")
        @Size(max = 1000)
        String notes
    ) {}
}
