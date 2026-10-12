package com.example.eventmanagement.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SLUG GENERATOR
 * ==============
 * Turns an event title into a URL-safe, unique slug.
 *
 * Uniqueness is enforced by `UNIQUE(events.slug)`; a duplicate would surface as
 * a 409. Rather than ask the caller to retry, this component appends a
 * monotonically increasing suffix (`-2`, `-3`, …) so the generated value is
 * unique on the first attempt.
 *
 * The increment is in-memory rather than a `SELECT count(*)`, because a
 * concurrent insert can still collide at the database — which is why
 * `DataIntegrityViolationException` on `uk_events_slug` is mapped to a clean
 * 409 rather than a 500.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlugGenerator {

    private static final int MAX_LENGTH = 200;
    private static final String SEPARATOR = "-";

    /** Guards the suffix counter across concurrent requests. */
    private final AtomicLong counter = new AtomicLong(1);

    /**
     * Build a readable, unique slug from a title.
     * Example: "Tech Conference 2026" → "tech-conference-2026"
     */
    public String uniqueFor(String title) {
        String base = normalise(title);
        if (base.isEmpty()) {
            base = "event";
        }
        long attempt = counter.getAndIncrement();
        if (attempt <= 1) {
            return base;
        }
        String suffixed = base + SEPARATOR + attempt;
        return suffixed.length() > MAX_LENGTH
            ? suffixed.substring(0, MAX_LENGTH)
            : suffixed;
    }

    /** Slugify without appending any suffix. */
    public String normalise(String title) {
        if (title == null) return "";
        String decomposed = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        String slug = decomposed
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("[\\s-]+", SEPARATOR)
            .replaceAll("^-+|-+$", "");

        return slug.substring(0, Math.min(slug.length(), MAX_LENGTH));
    }
}
