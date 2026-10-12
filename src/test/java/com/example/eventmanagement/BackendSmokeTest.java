package com.example.eventmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BackendSmokeTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void publicDiscoveryEndpointsReturnSeededContent() throws Exception {
        mvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(8));

        mvc.perform(get("/api/v1/events/featured").param("size", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").isNumber());

        mvc.perform(get("/api/v1/events").param("page", "0").param("size", "9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void registrationCreatesAccountAndRejectsDuplicateEmail() throws Exception {
        String email = "smoke-%s@example.com".formatted(UUID.randomUUID());
        String request = """
            {"name":"Smoke Test","email":"%s","password":"StrongPass123","role":"ATTENDEE"}
            """.formatted(email);

        mvc.perform(post("/api/v1/auth/register")
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5173"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.email").value(email));

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidCredentialsAndProtectedRoutesReturnJsonErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Weak Password","email":"weak@example.com","password":"weak","role":"ATTENDEE"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @org.springframework.test.annotation.DirtiesContext
    void organizerCanCreateEventWithSpotsTimeVenuePrice() throws Exception {
        // 1. Login as organizer
        String loginBody = """
            {"email":"organizer@eventora.io","password":"Admin@123"}
            """;
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");

        // 2. Create event with capacity (spots), time, venue, price
        String createEventBody = """
            {
                "title": "Global Tech Expo 2026",
                "shortDescription": "Cutting-edge event for developers and innovators.",
                "description": "Full conference with workshops and exhibitions.",
                "categoryId": 1,
                "startAt": "2026-11-20T09:00:00Z",
                "endAt": "2026-11-21T18:00:00Z",
                "venueName": "Silicon Arena",
                "addressLine": "500 Innovation Blvd",
                "city": "Bengaluru",
                "country": "India",
                "capacity": 300,
                "price": 499.0,
                "status": "PUBLISHED"
            }
            """;

        var createResult = mvc.perform(post("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEventBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("Global Tech Expo 2026"))
            .andExpect(jsonPath("$.data.capacity").value(300))
            .andExpect(jsonPath("$.data.availableSeats").value(300))
            .andExpect(jsonPath("$.data.price").value(499.0))
            .andExpect(jsonPath("$.data.venueName").value("Silicon Arena"))
            .andExpect(jsonPath("$.data.city").value("Bengaluru"))
            .andExpect(jsonPath("$.data.tickets.length()").value(1))
            .andExpect(jsonPath("$.data.tickets[0].price").value(499.0))
            .andExpect(jsonPath("$.data.tickets[0].quantityTotal").value(300))
            .andReturn();

        Number eventId = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Number tierId = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.tickets[0].id");

        // 3. Attendee registers for 2 spots on the event
        var attendeeLogin = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"attendee@eventora.io","password":"Admin@123"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String attendeeToken = com.jayway.jsonpath.JsonPath.read(attendeeLogin.getResponse().getContentAsString(), "$.data.accessToken");

        String registerBody = """
            {
                "ticketTierId": %d,
                "quantity": 2,
                "attendee": {
                    "fullName": "Test Attendee",
                    "email": "test@eventora.io",
                    "phone": "+91 99999 88888"
                },
                "paymentMethod": "CARD"
            }
            """.formatted(tierId.longValue());

        mvc.perform(post("/api/v1/events/" + eventId + "/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + attendeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.quantity").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(998.0))
            .andExpect(jsonPath("$.data.tickets.length()").value(2))
            .andExpect(jsonPath("$.data.tickets[0].ticketNumber").isNotEmpty());
    }
}
