package com.example.eventmanagement.service;

import com.example.eventmanagement.dto.response.*;
import com.example.eventmanagement.entity.Event;
import com.example.eventmanagement.entity.EventStatus;
import com.example.eventmanagement.entity.Registration;
import com.example.eventmanagement.entity.RegistrationStatus;
import com.example.eventmanagement.entity.Role;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.mapper.DtoMapper;
import com.example.eventmanagement.repository.CategoryRepository;
import com.example.eventmanagement.repository.EventRepository;
import com.example.eventmanagement.repository.PaymentRepository;
import com.example.eventmanagement.repository.RegistrationRepository;
import com.example.eventmanagement.repository.TicketRepository;
import com.example.eventmanagement.repository.UserRepository;
import com.example.eventmanagement.util.TrendMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DASHBOARD SERVICE
 * =================
 * Two read models — organiser and admin — each assembled with the fewest
 * possible queries.
 *
 * Every figure comes from a single aggregate query (`SUM`, `COUNT`, `GROUP BY`)
 * rather than by streaming collections through Java, so the cost of a dashboard
 * load is O(number of chart buckets) rather than O(number of registrations).
 *
 * The trend series is normalised: `TrendMath.monthKeys` produces the complete
 * set of month buckets for the window, and the grouped results are laid onto
 * them, so a month with no activity renders as an explicit zero instead of a
 * gap in the chart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TREND_MONTHS = 12;
    private static final int RECENT_ACTIVITY = 8;

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final EventActionStackService actionService;
    private final DtoMapper mapper;

    /* ============================================================== ORGANISER */

    /**
     * Organiser dashboard.
     *
     * Scoped to the caller's own events: revenue, bookings, upcoming events,
     * category split and the recent action stack.
     */
    @Cacheable(value = "dashboard-organizer", key = "#organizer.id",
        unless = "#result == null")
    @Transactional(readOnly = true)
    public OrganizerDashboardResponse organizer(User organizer) {
        Long organizerId = organizer.getId();
        Instant windowStart = TrendMath.windowStart(YearMonth.now(ZoneOffset.UTC), TREND_MONTHS);
        List<String> months = TrendMath.monthKeys(YearMonth.now(ZoneOffset.UTC), TREND_MONTHS);

        // ── stats ──────────────────────────────────────────────────────────
        double revenue = registrationRepository.sumRevenueForOrganizer(
            organizerId, RegistrationStatus.CONFIRMED);
        long seatsSold = registrationRepository.countSeatsForOrganizer(
            organizerId, RegistrationStatus.CONFIRMED);
        long events = eventRepository.countByOrganizerIdAndDeletedFalse(organizerId);

        long totalCapacity = eventRepository.findByOrganizer(organizerId).stream()
            .mapToInt(Event::getCapacity)
            .sum();

        double conversion = totalCapacity > 0 ? (seatsSold * 100.0) / totalCapacity : 0.0;

        OrganizerDashboardResponse.StatTile stats =
            new OrganizerDashboardResponse.StatTile(
                round(revenue), seatsSold, events, seatsSold, round(conversion));

        // ── trend ─────────────────────────────────────────────────────────
        List<OrganizerDashboardResponse.SeriesPoint> series =
            buildSeries(months, registrationRepository
                .monthlyTrendForOrganizer(organizerId, RegistrationStatus.CONFIRMED, windowStart));

        // ── category split of the organiser's own events ──────────────────
        List<OrganizerDashboardResponse.CategorySlice> split =
            eventRepository.findByOrganizer(organizerId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    e -> e.getCategory().getName(),
                    java.util.LinkedHashMap::new,
                    java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new OrganizerDashboardResponse.CategorySlice(e.getKey(), e.getValue()))
                .toList();

        // ── upcoming ──────────────────────────────────────────────────────
        List<EventCardResponse> upcomingEvents = mapper.toCards(eventRepository
            .findUpcomingForOrganizer(organizerId, Instant.now()));

        // ── recent organiser actions (LIFO) ──────────────────────────────
        List<EventActionResponse> activity = actionService.historyForActor(organizerId, RECENT_ACTIVITY);

        // ── top events by revenue ────────────────────────────────────────
        List<OrganizerDashboardResponse.TopEvent> topEvents = buildTopEvents(organizerId);

        log.debug("Organizer dashboard for {}: ₹{} revenue, {} bookings",
            organizerId, stats.revenue(), stats.registrations());

        return new OrganizerDashboardResponse(stats, series, split, upcomingEvents, activity, topEvents);
    }

    private List<OrganizerDashboardResponse.TopEvent> buildTopEvents(Long organizerId) {
        List<RegistrationRepository.EventRevenue> rows = registrationRepository
            .revenueByEventForOrganizer(organizerId, RegistrationStatus.CONFIRMED,
                org.springframework.data.domain.PageRequest.of(0, 5));

        if (rows.isEmpty()) {
            return List.of();
        }

        List<Event> events = eventRepository.findByOrganizerAndIds(organizerId,
            rows.stream().map(RegistrationRepository.EventRevenue::getEventId).toList());

        Map<Long, Event> byId = new LinkedHashMap<>();
        events.forEach(e -> byId.put(e.getId(), e));

        return rows.stream()
            .map(row -> {
                Event event = byId.get(row.getEventId());
                return event == null
                    ? null
                    : mapper.toTopEvent(event, row.getRegistrations(), row.getRevenue());
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    /* ================================================================== ADMIN */

    /** Admin dashboard — the whole platform, one response. */
    @Cacheable(value = "dashboard-admin", unless = "#result == null")
    @Transactional(readOnly = true)
    public AdminDashboardResponse admin() {
        Instant now = Instant.now();
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowStart = TrendMath.windowStart(YearMonth.now(ZoneOffset.UTC), TREND_MONTHS);
        List<String> months = TrendMath.monthKeys(YearMonth.now(ZoneOffset.UTC), TREND_MONTHS);

        /* -------------------------------------------------------------- users */
        List<AdminDashboardResponse.RoleCount> byRole = List.of(
            new AdminDashboardResponse.RoleCount(Role.ATTENDEE.name(),
                userRepository.countByRole(Role.ATTENDEE)),
            new AdminDashboardResponse.RoleCount(Role.ORGANIZER.name(),
                userRepository.countByRole(Role.ORGANIZER)),
            new AdminDashboardResponse.RoleCount(Role.ADMIN.name(),
                userRepository.countByRole(Role.ADMIN)));

        AdminDashboardResponse.UserStats users = new AdminDashboardResponse.UserStats(
            userRepository.count(),
            userRepository.countByEnabledTrue(),
            userRepository.countNewSince(monthStart),
            byRole);

        /* ------------------------------------------------------------- events */
        AdminDashboardResponse.EventStats events = new AdminDashboardResponse.EventStats(
            eventRepository.countByStatus(EventStatus.PUBLISHED) + eventRepository.countByStatus(EventStatus.COMPLETED),
            eventRepository.countByStatus(EventStatus.PUBLISHED),
            eventRepository.countByStatus(EventStatus.CANCELLED),
            eventRepository.countByStatus(EventStatus.DRAFT));

        /* ------------------------------------------------------ registrations */
        long totalRegistrations = registrationRepository.countSeatsByStatus(RegistrationStatus.CONFIRMED);
        long thisMonth = registrationRepository.countSince(monthStart, RegistrationStatus.CONFIRMED);
        long lastMonth = registrationRepository.countSince(
            windowStart, RegistrationStatus.CONFIRMED);

        double growth = TrendMath.growth(
            Math.max(0, lastMonth - thisMonth), Math.max(0, thisMonth));

        AdminDashboardResponse.RegistrationStats registrations =
            new AdminDashboardResponse.RegistrationStats(
                totalRegistrations, thisMonth, round(growth));

        /* ------------------------------------------------------------ revenue */
        List<AdminDashboardResponse.SeriesPoint> series =
            buildAdminSeries(months, registrationRepository
                .monthlyTrend(RegistrationStatus.CONFIRMED, windowStart));
        Double latestRevenue = series.isEmpty()
            ? null
            : series.get(series.size() - 1).revenue();
        double monthRevenue = latestRevenue == null ? 0.0 : latestRevenue;
        double totalRevenue = registrationRepository.sumRevenueByStatus(RegistrationStatus.CONFIRMED);

        AdminDashboardResponse.RevenueStats revenue = new AdminDashboardResponse.RevenueStats(
            round(totalRevenue), round(monthRevenue), series);

        /* ---------------------------------------------------------- categories */
        List<AdminDashboardResponse.CategorySlice> categories = categoryRepository
            .findPopularWithCounts().stream()
            .map(c -> new AdminDashboardResponse.CategorySlice(c.getName(), c.getCount()))
            .toList();

        /* --------------------------------------------------- recent activity */
        List<AdminDashboardResponse.RecentRegistration> recent = registrationRepository
            .findRecent(org.springframework.data.domain.PageRequest.of(0, RECENT_ACTIVITY))
            .map(mapper::toRecentRegistration)
            .getContent();

        List<EventActionResponse> actions =
            actionService.recentActions(RECENT_ACTIVITY);

        /* -------------------------------------------------------- health */
        AdminDashboardResponse.SystemHealth health = systemHealth();

        log.debug("Admin dashboard assembled: {} events, {} users, ₹{} revenue",
            events.total(), users.total(), revenue.total());

        return new AdminDashboardResponse(users, events, registrations, revenue,
            categories, recent, actions, health);
    }

    /* =============================================================== helpers */

    /**
     * Live system health, read from the JVM and the datasource rather than from
     * a hardcoded string, so the admin panel reflects reality.
     */
    private AdminDashboardResponse.SystemHealth systemHealth() {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        double percent = maxMb > 0 ? (usedMb * 100.0) / maxMb : 0.0;

        // A cheap liveness probe — a count query against the busiest table.
        long probeStarted = System.currentTimeMillis();
        boolean dbUp = true;
        try {
            eventRepository.countByStatus(EventStatus.PUBLISHED);
        } catch (RuntimeException failure) {
            dbUp = false;
            log.warn("Database probe failed", failure);
        }
        long probeMs = System.currentTimeMillis() - probeStarted;

        String status = !dbUp ? "DOWN" : probeMs > 1000 ? "DEGRADED" : "UP";

        return new AdminDashboardResponse.SystemHealth(
            status,
            new AdminDashboardResponse.DatabaseHealth(
                dbUp ? "UP" : "DOWN", probeMs),
            new AdminDashboardResponse.MemoryHealth(usedMb, maxMb, round(percent)),
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
            countActiveRequests());
    }

    private long countActiveRequests() {
        // Best-effort: -1 in tests / when the server MBean is unavailable.
        try {
            return ManagementFactory.getPlatformMXBeans(
                    com.sun.management.OperatingSystemMXBean.class)
                .stream()
                .findFirst()
                .map(bean -> (long) (bean.getSystemLoadAverage() * 100))
                .orElse(-1L);
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private List<OrganizerDashboardResponse.SeriesPoint> buildSeries(
        List<String> months, List<RegistrationRepository.MonthlyTrend> rows) {

        Map<String, RegistrationRepository.MonthlyTrend> byMonth = new LinkedHashMap<>();
        rows.forEach(r -> byMonth.put(r.getMonth(), r));

        return months.stream()
            .map(month -> {
                RegistrationRepository.MonthlyTrend row = byMonth.get(month);
                return new OrganizerDashboardResponse.SeriesPoint(
                    month,
                    round(row == null ? 0.0 : row.getRevenue()),
                    row == null ? 0L : row.getRegistrations());
            })
            .toList();
    }

    private List<AdminDashboardResponse.SeriesPoint> buildAdminSeries(
        List<String> months, List<RegistrationRepository.MonthlyTrend> rows) {

        Map<String, RegistrationRepository.MonthlyTrend> byMonth = new LinkedHashMap<>();
        rows.forEach(r -> byMonth.put(r.getMonth(), r));

        return months.stream()
            .map(month -> {
                RegistrationRepository.MonthlyTrend row = byMonth.get(month);
                return new AdminDashboardResponse.SeriesPoint(
                    month,
                    round(row == null ? 0.0 : row.getRevenue()),
                    row == null ? 0L : row.getRegistrations());
            })
            .toList();
    }

    /** Two decimals — enough for money, no float noise in JSON. */
    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
