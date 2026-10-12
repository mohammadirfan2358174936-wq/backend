package com.example.eventmanagement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-in payload. */
@Schema(name = "LoginRequest")
public record LoginRequest(

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "organizer@eventora.io")
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    String email,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Admin@123")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    String password
) {}
