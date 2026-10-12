package com.example.eventmanagement.config;

import com.example.eventmanagement.entity.Role;
import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventStatus;
import com.example.eventmanagement.repository.CategoryRepository;
import com.example.eventmanagement.repository.EventRepository;
import com.example.eventmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * DEMO DATA SEEDER
 * ================
 * On a fresh database, create the demo accounts, the category taxonomy and a
 * handful of realistic events, so a reviewer can log in and see a populated
 * product instead of empty screens.
 *
 * Guards:
 *  - Only runs on the `dev` / `default` (non-prod) profiles.
 *  - Idempotent: everything is guarded by `existsBy…`, so restarting the app
 *    never duplicates or resets the demo data.
 *  - Passwords are hashed through the real `PasswordEncoder`, never stored
 *    in plain text even for seeds.
 *
 * Demo credentials are printed once at startup.
 */
@Slf4j
@Component
@Profile({ "dev", "default", "postgres", "mysql", "test", "local" })
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email:admin@eventora.io}")
    private String adminEmail;

    @Value("${app.seed.admin-password:Admin@123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedCategories();
        seedEvents();
    }

    private void seedAdmin() {
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            var admin = com.example.eventmanagement.entity.User.builder()
                .name("Aarav Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
            userRepository.save(admin);
            log.info("Seeded admin account {}", adminEmail);
        }

        String organizerEmail = "organizer@eventora.io";
        if (userRepository.findByEmail(organizerEmail).isEmpty()) {
            var organizer = com.example.eventmanagement.entity.User.builder()
                .name("Irfan Organizer")
                .email(organizerEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ORGANIZER)
                .enabled(true)
                .build();
            userRepository.save(organizer);
            log.info("Seeded organiser account {}", organizerEmail);
        }

        String attendeeEmail = "attendee@eventora.io";
        if (userRepository.findByEmail(attendeeEmail).isEmpty()) {
            var attendee = com.example.eventmanagement.entity.User.builder()
                .name("Ayesha Attendee")
                .email(attendeeEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ATTENDEE)
                .enabled(true)
                .build();
            userRepository.save(attendee);
            log.info("Seeded attendee account {}", attendeeEmail);
        }
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) {
            return;
        }

        String[][] seeds = {
            { "Technology", "technology", "cpu", "#6366f1" },
            { "Music", "music", "music", "#d946ef" },
            { "Business", "business", "briefcase", "#22d3ee" },
            { "Sports", "sports", "trophy", "#34d399" },
            { "Arts & Culture", "arts-culture", "palette", "#fbbf24" },
            { "Food", "food", "utensils", "#fb7185" },
            { "Education", "education", "graduation-cap", "#8b5cf6" },
            { "Health", "health", "heart-pulse", "#3b82f6" },
        };

        for (String[] seed : seeds) {
            categoryRepository.save(com.example.eventmanagement.entity.Category.builder()
                .name(seed[0])
                .slug(seed[1])
                .icon(seed[2])
                .color(seed[3])
                .description("Discover the best %s events.".formatted(seed[0].toLowerCase()))
                .build());
        }
        log.info("Seeded {} event categories", seeds.length);
    }

    private void seedEvents() {
        if (eventRepository.count() > 0) return;

        var organizer = userRepository.findByEmail("organizer@eventora.io").orElseThrow();
        Instant now = Instant.now();
        List<DemoEvent> demos = List.of(
            new DemoEvent("Future of AI Summit", "future-of-ai-summit", "technology", 18, 2,
                "A day of big ideas and practical conversations about what comes next in artificial intelligence.",
                "https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1400&q=85", 1499, true),
            new DemoEvent("Under the Open Sky", "under-the-open-sky", "music", 26, 1,
                "An open-air evening with independent artists, warm lights and room to dance.",
                "https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=1400&q=85", 799, true),
            new DemoEvent("The Sunday Table", "the-sunday-table", "food", 34, 4,
                "A long-table lunch celebrating local kitchens, seasonal produce and the joy of eating together.",
                "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1400&q=85", 1200, false),
            new DemoEvent("Designing Better Cities", "designing-better-cities", "arts-culture", 43, 3,
                "Meet the people reshaping public spaces, from walkable streets to welcoming neighbourhoods.",
                "https://images.unsplash.com/photo-1511818966892-d7d671e672a2?auto=format&fit=crop&w=1400&q=85", 0, false)
        );

        for (DemoEvent demo : demos) {
            var category = categoryRepository.findBySlug(demo.categorySlug()).orElseThrow();
            Instant start = now.plus(Duration.ofDays(demo.startInDays()));
            Event event = Event.builder()
                .title(demo.title())
                .slug(demo.slug())
                .description(demo.description())
                .shortDescription(demo.description())
                .coverImageUrl(demo.coverImageUrl())
                .category(category)
                .organizer(organizer)
                .startAt(start)
                .endAt(start.plus(Duration.ofDays(demo.durationDays())))
                .venueName("HITEX Exhibition Center")
                .addressLine("Izzat Nagar, Madhapur")
                .city("Hyderabad")
                .country("India")
                .capacity(240)
                .availableSeats(240)
                .registrationCount(0)
                .price(demo.price())
                .status(EventStatus.PUBLISHED)
                .publishedAt(now)
                .featured(demo.featured())
                .tags(List.of(demo.categorySlug(), "community", "eventora"))
                .build();

            event.addTicket(com.example.eventmanagement.entity.TicketTier.builder()
                .name("General Admission")
                .description("Standard access to all stages and exhibition areas.")
                .price(demo.price())
                .quantityTotal(200)
                .quantitySold(0)
                .maxPerOrder(10)
                .active(true)
                .build());

            event.addTicket(com.example.eventmanagement.entity.TicketTier.builder()
                .name("VIP Pass")
                .description("Priority seating, lounge access and backstage entry.")
                .price(demo.price() > 0 ? (double) Math.round(demo.price() * 1.8) : 499.0)
                .quantityTotal(40)
                .quantitySold(0)
                .maxPerOrder(4)
                .active(true)
                .build());

            eventRepository.save(event);
        }
        log.info("Seeded {} sample events", demos.size());
    }

    private record DemoEvent(String title, String slug, String categorySlug, int startInDays,
                             int durationDays, String description, String coverImageUrl,
                             double price, boolean featured) {}
}
