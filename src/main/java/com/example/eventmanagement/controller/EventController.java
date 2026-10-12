package com.example.eventmanagement.controller;

import com.example.eventmanagement.dto.request.CreateEventRequest;
import com.example.eventmanagement.dto.request.EventQueryParams;
import com.example.eventmanagement.dto.request.UpdateEventRequest;
import com.example.eventmanagement.dto.response.CategoryResponse;
import com.example.eventmanagement.dto.response.EventActionResponse;
import com.example.eventmanagement.dto.response.EventCardResponse;
import com.example.eventmanagement.dto.response.EventDetailResponse;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.exception.CategoryNotFoundException;
import com.example.eventmanagement.mapper.DtoMapper;
import com.example.eventmanagement.repository.CategoryRepository;
import com.example.eventmanagement.service.EventActionService;
import com.example.eventmanagement.service.EventService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Event endpoints including discovery, authoring, lifecycle and action stack. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventActionService actionService;
    private final CategoryRepository categoryRepository;
    private final DtoMapper mapper;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse.PagedBody<EventCardResponse>> discover(
        EventQueryParams params,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(eventService.discover(params, actor));
    }

    @GetMapping("/events/featured")
    public ResponseEntity<ApiResponse.ApiBody<List<EventCardResponse>>> featured(
        @RequestParam(defaultValue = "6") int size) {
        return ResponseEntity.ok(ApiResponse.success(
            eventService.featured(Math.min(Math.max(size, 1), 24)), "Featured events"));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<ApiResponse.ApiBody<EventDetailResponse>> detail(
        @PathVariable Long id,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.success(eventService.detail(id, actor), "Event details"));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse.ApiBody<EventDetailResponse>> create(
        @Valid @RequestBody CreateEventRequest request,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(eventService.create(request, actor), "Event created successfully"));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<ApiResponse.ApiBody<EventDetailResponse>> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateEventRequest request,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.success(eventService.update(id, request, actor), "Event updated successfully"));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<ApiResponse.ApiBody<Void>> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal User actor) {
        eventService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.success(null, "Event deleted successfully"));
    }

    @PostMapping("/events/{id}/publish")
    public ResponseEntity<ApiResponse.ApiBody<EventDetailResponse>> publish(
        @PathVariable Long id,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.success(eventService.publish(id, actor), "Event published successfully"));
    }

    @PostMapping("/events/{id}/cancel")
    public ResponseEntity<ApiResponse.ApiBody<EventDetailResponse>> cancel(
        @PathVariable Long id,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.success(eventService.cancel(id, actor), "Event cancelled successfully"));
    }

    @GetMapping("/events/{id}/history")
    public ResponseEntity<ApiResponse.ApiBody<List<EventActionResponse>>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(actionService.history(id), "Event action history"));
    }

    @PostMapping("/events/{id}/undo")
    public ResponseEntity<ApiResponse.ApiBody<EventActionResponse>> undo(
        @PathVariable Long id,
        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.success(actionService.undo(id, actor), "Undid latest event action"));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse.ApiBody<List<CategoryResponse>>> categories() {
        List<CategoryResponse> categories = categoryRepository.findAllByOrderByNameAsc()
            .stream().map(mapper::toCategoryResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(categories, "Categories"));
    }

    @GetMapping("/categories/{slug}/events")
    public ResponseEntity<ApiResponse.PagedBody<EventCardResponse>> categoryEvents(
        @PathVariable String slug,
        EventQueryParams params,
        @AuthenticationPrincipal User actor) {
        if (categoryRepository.findBySlug(slug).isEmpty()) {
            throw new CategoryNotFoundException(slug);
        }
        EventQueryParams categoryParams = new EventQueryParams(
            params.page(), params.size(), params.sort(), slug, params.status(), params.city(),
            params.q(), params.from(), params.to(), params.minPrice(), params.maxPrice(), params.featured());
        return ResponseEntity.ok(eventService.discover(categoryParams, actor));
    }
}
