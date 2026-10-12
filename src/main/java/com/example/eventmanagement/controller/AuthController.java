package com.example.eventmanagement.controller;

import com.example.eventmanagement.dto.request.LoginRequest;
import com.example.eventmanagement.dto.request.RegisterUserRequest;
import com.example.eventmanagement.dto.response.AuthResponse;
import com.example.eventmanagement.dto.response.UserResponse;
import com.example.eventmanagement.service.AuthService;
import com.example.eventmanagement.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH CONTROLLER
 * ==============
 * Registration, login, profile, logout.
 */
@Tag(name = "Authentication", description = "Registration, login and profile")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Create an account.
     *
     * Role is accepted but sanitised in the service: `ADMIN` can never be
     * self-assigned, only `ORGANIZER` or `ATTENDEE`.
     */
    @Operation(summary = "Register a new account",
        description = "Creates an account and returns a JWT that is valid for 24 hours.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiResponse.ApiBody.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ApiResponse.ApiBody.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse.ApiBody<AuthResponse>> register(
        @Valid @RequestBody RegisterUserRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(response, "Account created successfully"));
    }

    @Operation(summary = "Sign in", description = "Exchanges credentials for a JWT.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse.ApiBody<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Signed in successfully"));
    }

    @Operation(summary = "Current user", description = "The profile of the authenticated caller.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse.ApiBody<UserResponse>> me(
        @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(ApiResponse.success(
            authService.me(principal.getUsername()), "Profile retrieved"));
    }

    /**
     * Stateless logout. The JWT cannot be revoked server-side (that is the
     * trade-off of a stateless design), so the authoritative action is for the
     * client to discard the token — the endpoint exists for auditing.
     */
    @Operation(summary = "Log out")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse.ApiBody<Void>> logout(
        @AuthenticationPrincipal UserDetails principal) {

        authService.logout(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Signed out"));
    }
}
