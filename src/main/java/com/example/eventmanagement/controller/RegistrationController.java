package com.example.eventmanagement.controller;

import com.example.eventmanagement.dto.request.RegisterRequest;
import com.example.eventmanagement.dto.response.EventCardResponse;
import com.example.eventmanagement.dto.response.RegistrationResponse;
import com.example.eventmanagement.dto.response.TicketResponse;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.service.RegistrationService;
import com.example.eventmanagement.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Registration, ticketing, check-in and user booking history endpoints. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/events/{id}/register")
    public ResponseEntity<ApiResponse.ApiBody<RegistrationResponse>> register(
        @PathVariable Long id,
        @Valid @RequestBody RegisterRequest request,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(registrationService.register(id, request, actor), "Registered successfully"));
    }

    @DeleteMapping("/events/{id}/register")
    public ResponseEntity<ApiResponse.ApiBody<Void>> cancel(
        @PathVariable Long id,
        @AuthenticationPrincipal User actor) {
        registrationService.cancel(id, actor);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking cancelled successfully"));
    }

    @GetMapping("/users/me/events")
    public ResponseEntity<ApiResponse.PagedBody<EventCardResponse>> myEvents(
        @AuthenticationPrincipal User actor,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(registrationService.myEvents(actor, page, size));
    }

    @GetMapping("/users/me/registrations")
    public ResponseEntity<ApiResponse.PagedBody<RegistrationResponse>> myRegistrations(
        @AuthenticationPrincipal User actor,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(registrationService.myRegistrations(actor, page, size));
    }

    @GetMapping("/users/me/tickets")
    public ResponseEntity<ApiResponse.PagedBody<TicketResponse>> myTickets(
        @AuthenticationPrincipal User actor,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(registrationService.myTickets(actor, page, size));
    }

    @GetMapping("/tickets/{ticketNumber}")
    public ResponseEntity<ApiResponse.ApiBody<TicketResponse>> ticket(@PathVariable String ticketNumber) {
        return ResponseEntity.ok(ApiResponse.success(registrationService.ticket(ticketNumber), "Ticket retrieved"));
    }

    @PostMapping("/tickets/{ticketNumber}/validate")
    public ResponseEntity<ApiResponse.ApiBody<TicketResponse>> validate(@PathVariable String ticketNumber) {
        return ResponseEntity.ok(ApiResponse.success(registrationService.validate(ticketNumber), "Ticket validated"));
    }
}
