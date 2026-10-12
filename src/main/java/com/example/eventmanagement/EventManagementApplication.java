/**
 * ENTRY POINT
 * -----------
 * Bootstraps the Spring Boot application.
 *
 * `@EnableCaching` activates Spring's cache abstraction. Two cache names are
 * declared in `CacheConfig` (`categories`, `featured-events`); anything not
 * declared is rejected at runtime so cache typos surface in dev, not prod.
 *
 * `@EnableScheduling` drives the light background job that flips COMPLETED
 * events once their end time passes — see `EventLifecycleScheduler`.
 */
package com.example.eventmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({com.example.eventmanagement.security.SecurityProperties.class})
public class EventManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventManagementApplication.class, args);
    }
}
