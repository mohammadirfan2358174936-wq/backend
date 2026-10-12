package com.example.eventmanagement.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * CACHE CONFIGURATION
 * ===================
 * Two named caches, backed by Caffeine (an in-process, near-optimal Guava
 * successor). Every cache used by the application is declared here — request a
 * cache name that is not on this list and the container rejects it at runtime,
 * which turns a typo into a startup failure instead of a silent bypass.
 *
 * | Cache | What it holds | Why |
 * |---|---|---|
 * | `featured-events` | the landing-page carousel | read on every visit, changes only when an event is published |
 * | `categories` | the taxonomy | effectively immutable; exists only to keep the client in sync |
 * | `dashboard-organizer` | one organiser's rollups | expensive `SUM`/`GROUP BY` over `registrations` |
 * | `dashboard-admin` | platform-wide rollups | same, plus N queries |
 *
 * Eviction is explicit: every write path in `EventService` evicts
 * `featured-events`/`categories` with `@CacheEvict(allEntries = true)`, so
 * cached data can never go stale for longer than a code path change. The
 * dashboard caches are time-bounded (5 min) because their inputs change
 * constantly through reads that do not go through the event write path.
 *
 * The cache is local, which is the right default here: a single-node
 * deployment has no cross-node coherence problem, and Redis is activated only
 * under the `redis` profile.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String FEATURED_EVENTS = "featured-events";
    public static final String CATEGORIES = "categories";
    public static final String DASHBOARD_ORGANIZER = "dashboard-organizer";
    public static final String DASHBOARD_ADMIN = "dashboard-admin";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            FEATURED_EVENTS, CATEGORIES, DASHBOARD_ORGANIZER, DASHBOARD_ADMIN);

        // Default entry limits and evictions for anything added later.
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());

        // Short-lived, high-churn rollups.
        manager.registerCustomCache(DASHBOARD_ORGANIZER, Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build());

        manager.registerCustomCache(DASHBOARD_ADMIN, Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build());

        // Category data is effectively immutable.
        manager.registerCustomCache(CATEGORIES, Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .recordStats()
            .build());

        log.info("Cache manager ready: {}", manager.getCacheNames());
        return manager;
    }
}
