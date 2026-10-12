package com.example.eventmanagement.util;

/**
 * Deterministic date maths for the dashboard trend window.
 *
 * The charts show the last 12 months, so the series needs a fixed bucket list
 * even in months where nothing happened — a missing month would render as a
 * gap in the line chart rather than a zero.
 */
public final class TrendMath {

    private TrendMath() {
    }

    /** `YYYY-MM` for the month `offset` months before `anchor`. */
    public static String monthKey(java.time.YearMonth anchor, int offset) {
        return anchor.minusMonths(offset).toString();
    }

    /**
     * The `count` month keys ending at `anchor`, oldest first.
     * Example with anchor = 2026-10 and count = 3 → ["2026-08", "2026-09", "2026-10"].
     */
    public static java.util.List<String> monthKeys(java.time.YearMonth anchor, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
            .mapToObj(i -> monthKey(anchor, count - i))
            .toList();
    }

    /** Start-of-month instant used as the inclusive lower bound of the trend. */
    public static java.time.Instant windowStart(java.time.YearMonth anchor, int count) {
        return anchor.minusMonths(count - 1L)
            .atDay(1)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant();
    }

    /** Percentage change, guarding a zero baseline. */
    public static double growth(double previous, double current) {
        if (previous <= 0) {
            return current <= 0 ? 0.0 : 100.0;
        }
        return ((current - previous) / previous) * 100.0;
    }
}
