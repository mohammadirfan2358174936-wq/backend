package com.example.eventmanagement.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Small, dependency-free utility belt.
 */
public final class AppUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private AppUtils() {
    }

    /** `EVT-7X4KQ9` — a human-readable booking reference. */
    public static String bookingReference() {
        return "EVT-" + randomCode(6);
    }

    /** `EVT-7X4KQ9-003` — one ticket inside a multi-seat booking. */
    public static String ticketNumber(String bookingRef, int index, int total) {
        String suffix = "%0" + String.valueOf(total).length() + "d";
        return "%s-%s".formatted(bookingRef, String.format(suffix, index + 1));
    }

    /** Idempotency key for the payment gateway. */
    public static String transactionReference(Long registrationId) {
        return "txn-%d-%s".formatted(registrationId, randomCode(10));
    }

    private static String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i += 1) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    /** The authenticated principal's email, if any. */
    public static Optional<String> currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String name && !"anonymousUser".equals(name)) {
            return Optional.of(name);
        }
        return Optional.ofNullable(authentication.getName());
    }

    /** Clamp a nullable int into [min, max]. */
    public static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) return fallback;
        return Math.min(Math.max(value, min), max);
    }
}
