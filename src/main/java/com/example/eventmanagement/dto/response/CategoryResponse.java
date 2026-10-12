package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CategoryResponse")
public record CategoryResponse(
    Long id,
    String name,
    String slug,
    String icon,
    String color,
    String description
) {}
