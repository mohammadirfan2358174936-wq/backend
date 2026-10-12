package com.example.eventmanagement.controller;

import com.example.eventmanagement.dto.response.AdminDashboardResponse;
import com.example.eventmanagement.dto.response.OrganizerDashboardResponse;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.service.DashboardService;
import com.example.eventmanagement.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organizer and Admin dashboard aggregation endpoints. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/organizer")
    public ResponseEntity<ApiResponse.ApiBody<OrganizerDashboardResponse>> organizer(
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.organizer(actor), "Organizer dashboard"));
    }

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse.ApiBody<AdminDashboardResponse>> admin() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.admin(), "Admin dashboard"));
    }
}
