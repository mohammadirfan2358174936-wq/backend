package com.example.eventmanagement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registration payload for the sign-up screen. */
@Schema(name = "RegisterUserRequest")
public record RegisterUserRequest(

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Irfan Ahmed")
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
    String name,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "irfan@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 180, message = "Email cannot exceed 180 characters")
    String email,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Str0ng@Pass")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
        message = "Password must contain at least one uppercase letter, one lowercase letter and one digit")
    String password,

    @Schema(example = "+91 98765 43210")
    @Size(max = 32)
    String phone,

    @Schema(description = "Role to assign. Only ORGANIZER and ATTENDEE are self-service; ADMIN is seeded.", example = "ATTENDEE")
    String role
) {}
