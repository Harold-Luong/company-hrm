package com.company.calendar_service.calendar;

import com.company.calendar_service.JwtTestSupport;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "EMPLOYEE")
class CalendarIntegrationTests extends JwtTestSupport {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearEventsWithinRolledBackTransaction() {
        jdbc.update("DELETE FROM calendar_events");
    }

    @Test
    @WithAnonymousUser
    void healthRemainsPublicButCalendarRequiresLogin() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/api/v1/calendar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsDatabaseFieldsThroughDtoAndHidesDraftsAndInternalFields() throws Exception {
        allDay("Public holiday", "PUBLISHED", "2026-09-02", "2026-09-02");
        allDay("Cancelled holiday", "CANCELLED", "2026-09-03", "2026-09-03");
        allDay("Private draft", "DRAFT", "2026-09-04", "2026-09-04");
        jdbc.update("UPDATE calendar_events SET description = ?, location = ? WHERE title = ?",
                "Current database description", "Office", "Public holiday");

        mvc.perform(get("/api/v1/calendar").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].id", isA(Number.class)))
                .andExpect(jsonPath("$.events[0].title").value("Public holiday"))
                .andExpect(jsonPath("$.events[0].description").value("Current database description"))
                .andExpect(jsonPath("$.events[0].type").value("HOLIDAY"))
                .andExpect(jsonPath("$.events[0].holidayKind").value("PUBLIC_HOLIDAY"))
                .andExpect(jsonPath("$.events[0].allDay").value(true))
                .andExpect(jsonPath("$.events[0].startDate").value("2026-09-02"))
                .andExpect(jsonPath("$.events[0].endDate").value("2026-09-02"))
                .andExpect(jsonPath("$.events[0].startAt").value(nullValue()))
                .andExpect(jsonPath("$.events[0].endAt").value(nullValue()))
                .andExpect(jsonPath("$.events[0].timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.events[0].location").value("Office"))
                .andExpect(jsonPath("$.events[0].audienceType").value("ALL"))
                .andExpect(jsonPath("$.events[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.events[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.events[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.events[0].updatedBy").doesNotExist())
                .andExpect(jsonPath("$.events[0].version").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(jsonPath("$.legend").doesNotExist());
    }

    @Test
    void allDayRangesIncludeBothEndDatesAndEveryCrossedYear() throws Exception {
        allDay("Crosses multiple years", "PUBLISHED", "2024-12-31", "2026-01-01");
        allDay("Ends before year", "PUBLISHED", "2025-12-30", "2025-12-31");
        allDay("Starts next year", "PUBLISHED", "2027-01-01", "2027-01-01");
        allDay("Hidden year", "DRAFT", "2030-01-01", "2030-01-01");

        mvc.perform(get("/api/v1/calendar").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].title").value("Crosses multiple years"))
                .andExpect(jsonPath("$.events[0].startDate").value("2024-12-31"))
                .andExpect(jsonPath("$.availableYears", hasItems(2024, 2025, 2026, 2027)))
                .andExpect(jsonPath("$.availableYears", not(hasItem(2030))));
    }

    @Test
    void timedRangesUseLocalYearBoundariesAndExclusiveEnd() throws Exception {
        timed("Ends at year start", "2025-12-31T23:00:00+07:00", "2026-01-01T00:00:00+07:00");
        timed("Crosses year start", "2025-12-31T23:00:00+07:00", "2026-01-01T01:00:00+07:00");
        allDay("All day at year start", "PUBLISHED", "2026-01-01", "2026-01-01");
        timed("Starts at year start", "2026-01-01T00:00:00+07:00", "2026-01-01T02:00:00+07:00");
        timed("Ends at next year", "2026-12-31T23:00:00+07:00", "2027-01-01T00:00:00+07:00");
        timed("Starts next year", "2027-01-01T00:00:00+07:00", "2027-01-01T01:00:00+07:00");

        mvc.perform(get("/api/v1/calendar").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].title", contains(
                        "Crosses year start", "All day at year start", "Starts at year start", "Ends at next year")))
                .andExpect(jsonPath("$.events[0].type").value("MEETING"))
                .andExpect(jsonPath("$.events[0].allDay").value(false))
                .andExpect(jsonPath("$.events[0].holidayKind").value(nullValue()))
                .andExpect(jsonPath("$.events[0].startDate").value(nullValue()))
                .andExpect(jsonPath("$.events[0].endDate").value(nullValue()))
                .andExpect(jsonPath("$.events[0].startAt").value("2025-12-31T16:00:00Z"))
                .andExpect(jsonPath("$.events[0].endAt").value("2025-12-31T18:00:00Z"));
    }

    @Test
    void availableYearsExcludeYearAtExclusiveTimedEnd() throws Exception {
        timed("Midnight boundary", "2040-12-31T23:00:00+07:00", "2041-01-01T00:00:00+07:00");

        mvc.perform(get("/api/v1/calendar").param("year", "2040"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableYears", hasItem(2040)))
                .andExpect(jsonPath("$.availableYears", not(hasItem(2041))));
    }

    @Test
    void emptyDatabaseReturnsAnEmptyCalendarWithDefaultAndSelectedYears() throws Exception {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        mvc.perform(get("/api/v1/calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(currentYear))
                .andExpect(jsonPath("$.availableYears", contains(currentYear)))
                .andExpect(jsonPath("$.events", empty()));

        mvc.perform(get("/api/v1/calendar").param("year", "2035"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2035))
                .andExpect(jsonPath("$.availableYears", hasItems(currentYear, 2035)))
                .andExpect(jsonPath("$.events", empty()));
    }

    @Test
    @WithAnonymousUser
    void swaggerLoadsUiConfigurationAndApiDocumentWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/openapi/calendar-api.yml"));
        mvc.perform(get("/openapi/calendar-api.yml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/calendar:")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/openapi/calendar-api.yml")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidYearsAndDoesNotExposeWrites() throws Exception {
        for (String year : new String[] { "1899", "2101", "invalid" }) {
            mvc.perform(get("/api/v1/calendar").param("year", year))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/calendar")).andExpect(status().isForbidden());
    }

    private void allDay(String title, String status, String start, String end) {
        jdbc.update("""
                INSERT INTO calendar_events
                    (title, type, holiday_kind, all_day, start_date, end_date, status, created_by, updated_by)
                VALUES (?, 'HOLIDAY', 'PUBLIC_HOLIDAY', true, ?::date, ?::date, ?, 'test-actor', 'test-actor')
                """, title, start, end, status);
    }

    private void timed(String title, String start, String end) {
        jdbc.update("""
                INSERT INTO calendar_events
                    (title, type, all_day, start_at, end_at, status, created_by, updated_by)
                VALUES (?, 'MEETING', false, ?::timestamptz, ?::timestamptz, 'PUBLISHED', 'test-actor', 'test-actor')
                """, title, start, end);
    }
}
