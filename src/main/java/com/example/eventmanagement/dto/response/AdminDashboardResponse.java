package com.example.eventmanagement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** `GET /dashboard/admin` */
@Schema(name = "AdminDashboardResponse")
public record AdminDashboardResponse(
    UserStats users,
    EventStats events,
    RegistrationStats registrations,
    RevenueStats revenue,
    List<CategorySlice> popularCategories,
    List<RecentRegistration> recentRegistrations,
    List<EventActionResponse> recentActivity,
    SystemHealth systemHealth
) {

    @Schema(name = "UserStats")
    public record UserStats(
        Long total,
        Long active,
        Long newThisMonth,
        List<RoleCount> byRole
    ) {}

    @Schema(name = "RoleCount")
    public record RoleCount(
        String role,
        Long count
    ) {}

    @Schema(name = "EventStats")
    public record EventStats(
        Long total,
        Long published,
        Long cancelled,
        Long draft
    ) {}

    @Schema(name = "RegistrationStats")
    public record RegistrationStats(
        Long total,
        Long thisMonth,
        Double growthPercent
    ) {}

    @Schema(name = "RevenueStats")
    public record RevenueStats(
        Double total,
        Double thisMonth,
        List<SeriesPoint> series
    ) {}

    @Schema(name = "SeriesPoint")
    public record SeriesPoint(
        String month,
        Double revenue,
        Long registrations
    ) {}

    @Schema(name = "CategorySlice")
    public record CategorySlice(
        String name,
        Long value
    ) {}

    @Schema(name = "RecentRegistration")
    public record RecentRegistration(
        Long id,
        String attendee,
        String eventTitle,
        Double amount,
        String createdAt
    ) {}

    @Schema(name = "SystemHealth")
    public record SystemHealth(
        String status,
        DatabaseHealth database,
        MemoryHealth memory,
        Long uptimeSeconds,
        Long activeRequests
    ) {}

    @Schema(name = "DatabaseHealth")
    public record DatabaseHealth(
        String status,
        Long responseTimeMs
    ) {}

    @Schema(name = "MemoryHealth")
    public record MemoryHealth(
        Long usedMb,
        Long maxMb,
        Double percent
    ) {}
}
