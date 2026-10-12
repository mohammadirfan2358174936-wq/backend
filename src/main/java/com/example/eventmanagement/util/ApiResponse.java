package com.example.eventmanagement.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * The single response envelope used by every endpoint.
 *
 * ```json
 * { "success": true, "message": "...", "data": ..., "timestamp": "..." }
 * ```
 *
 * Wrapping rather than returning bare payloads has two pay-offs: the client can
 * branch on `success` without inspecting status codes, and pagination metadata
 * travels next to the data instead of needing a parallel structure.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiResponse {

    public static <T> ApiBody<T> success(T data, String message) {
        return new ApiBody<>(true, message, data, Instant.now());
    }

    public static <T> ApiBody<T> success(T data) {
        return success(data, "OK");
    }

    public static <T> ApiBody<T> created(T data, String message) {
        return success(data, message);
    }

    public static <T> ApiBody<T> failure(String message, String errorCode) {
        return new ApiBody<>(false, message, null, Instant.now(), errorCode, null);
    }

    public static <T> ApiBody<T> failure(String message, String errorCode, java.util.Map<String, String> errors) {
        return new ApiBody<>(false, message, null, Instant.now(), errorCode, errors);
    }

    /* ----------------------------------------------------------- paged wrappers */

    /**
     * Wrap a Spring `Page` while mapping its content through `mapper`.
     * The mapping happens inside the `Page.map` call, so it runs exactly once
     * per element and never materialises an intermediate list.
     */
    public static <S, T> PagedBody<T> paged(Page<S> page, Function<S, T> mapper, String message) {
        return new PagedBody<>(
            true,
            message,
            page.map(mapper).getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            Instant.now());
    }

    public static <S, T> PagedBody<T> paged(Page<S> page, Function<S, T> mapper) {
        return paged(page, mapper, "OK");
    }

    public static <S, T> PagedBody<T> of(List<S> list, Function<S, T> mapper, String message) {
        return new PagedBody<>(
            true, message,
            list.stream().map(mapper).toList(),
            0, list.size(), list.size(), list.isEmpty() ? 0 : 1,
            true, list.size() <= 1, Instant.now());
    }

    public static <S, T> PagedBody<T> of(List<S> list, Function<S, T> mapper) {
        return of(list, mapper, "OK");
    }

    /** Payload container for a success response. */
    public record ApiBody<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp,
        String errorCode,
        java.util.Map<String, String> errors
    ) {
        public ApiBody(boolean success, String message, T data, Instant timestamp) {
            this(success, message, data, timestamp, success ? null : "ERROR", null);
        }
    }

    /** Payload container for a paginated response. */
    public record PagedBody<T>(
        boolean success,
        String message,
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        Instant timestamp
    ) {
    }
}
