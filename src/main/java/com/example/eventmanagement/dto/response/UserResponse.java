package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserResponse")
public record UserResponse(
    Long id,
    String name,
    String email,
    String phone,
    String role,
    String avatarUrl,
    String bio,
    String createdAt
) {}
