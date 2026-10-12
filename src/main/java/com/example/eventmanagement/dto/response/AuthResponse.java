package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse")
public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserResponse user
) {}
