package com.example.eventmanagement.service;

import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.Event.EventSnapshot;
import com.example.eventmanagement.entity.EventStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an `Event` into the JSON snapshot stored on an `EventAction`.
 *
 * Snapshots are what make `undo` possible: reversing an action means restoring
 * the exact field values that existed before it. Storing the whole mutable
 * surface — not just the changed field — keeps `undo` correct when several
 * fields were mutated together, and means a restore never has to know which
 * fields a given action type touches.
 *
 * Deliberately excluded from the snapshot:
 *  - identity (`id`, `slug`) — must never be undone;
 *  - denormalised counters (`registrationCount`, `availableSeats`) — they follow
 *    the registrations, not the snapshot;
 *  - audit columns (`createdAt`, `updatedAt`, `version`).
 */
@Component
public class EventActionSnapshotter {

    /** JSON view of an event, in a stable key order. */
    public Map<String, Object> snapshot(Event event) {
        return event == null ? Map.of() : toMap(event.snapshot());
    }

    /** Snapshot representing "nothing yet" — used for `CREATE_EVENT`. */
    public Map<String, Object> empty() {
        return Map.of();
    }

    /**
     * Write a snapshot back onto a live event.
     *
     * Capacity is restored, and `availableSeats` is recomputed from it so the
     * invariant `availableSeats = capacity - seatsSold` can never be broken by
     * an undo — the registration count is the single source of truth.
     */
    public void restore(Event target, Map<String, Object> snapshot) {
        if (target == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }

        String title = asString(snapshot.get("title"));
        if (title != null) target.setTitle(title);

        String venue = asString(snapshot.get("venueName"));
        if (venue != null) target.setVenueName(venue);

        String address = asString(snapshot.get("addressLine"));
        if (address != null) target.setAddressLine(address);

        String city = asString(snapshot.get("city"));
        if (city != null) target.setCity(city);

        String country = asString(snapshot.get("country"));
        if (country != null) target.setCountry(country);

        String description = asString(snapshot.get("description"));
        if (description != null) target.setDescription(description);

        String shortDescription = asString(snapshot.get("shortDescription"));
        if (shortDescription != null) target.setShortDescription(shortDescription);

        String cover = asString(snapshot.get("coverImageUrl"));
        if (cover != null) target.setCoverImageUrl(cover);

        String status = asString(snapshot.get("status"));
        if (status != null) {
            try {
                target.setStatus(EventStatus.valueOf(status));
            } catch (IllegalArgumentException ignored) {
                // Unknown value in an old snapshot — keep the current status.
            }
        }

        Integer capacity = asInt(snapshot.get("capacity"));
        if (capacity != null) {
            target.setCapacity(capacity);
            // Keep the invariant: seats already sold can never exceed capacity.
            int sold = Math.max(0, target.getRegistrationCount());
            target.setAvailableSeats(Math.max(0, capacity - sold));
        }

        Double price = asDouble(snapshot.get("price"));
        if (price != null) target.setPrice(price);

        Boolean featured = asBoolean(snapshot.get("featured"));
        if (featured != null) target.setFeatured(featured);

        Object tags = snapshot.get("tags");
        if (tags instanceof List<?> list) {
            target.setTags(list.stream().filter(String.class::isInstance).map(String.class::cast).toList());
        }
    }

    /* ---------------------------------------------------------------- helpers */

    private Map<String, Object> toMap(EventSnapshot s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", s.title());
        map.put("status", s.status() == null ? null : s.status().name());
        map.put("capacity", s.capacity());
        map.put("availableSeats", s.availableSeats());
        map.put("price", s.price());
        map.put("venueName", s.venueName());
        map.put("addressLine", s.addressLine());
        map.put("city", s.city());
        map.put("country", s.country());
        map.put("startAt", s.startAt() == null ? null : s.startAt().toString());
        map.put("endAt", s.endAt() == null ? null : s.endAt().toString());
        map.put("description", s.description());
        map.put("shortDescription", s.shortDescription());
        map.put("coverImageUrl", s.coverImageUrl());
        map.put("featured", s.featured());
        map.put("tags", s.tags() == null ? List.of() : s.tags());
        return map;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Boolean asBoolean(Object value) {
        return value instanceof Boolean b ? b : null;
    }
}
