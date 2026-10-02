package com.company.calendar_service.calendar;

import com.company.calendar_service.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "hr-actor", roles = "HR")
class CalendarManagementIntegrationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private static final String BODY = """
            {"title":"  Company holiday  ","description":"Description","type":"HOLIDAY",
             "holidayKind":"COMPANY_DAY_OFF","allDay":true,"startDate":"2090-01-15",
             "endDate":"2090-01-16","timezone":"Asia/Ho_Chi_Minh","location":"Office","audienceType":"ALL"}
            """;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("DELETE FROM calendar_event_audit");
        jdbc.update("DELETE FROM calendar_events");
    }

    @Test
    void hrCreatesReadsReplacesAndDeletesDraftWithAuditAndVersion() throws Exception {
        long id = create();
        mvc.perform(get("/api/v1/calendar-events/{id}", id))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.title").value("Company holiday"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("hr-actor"));
        mvc.perform(get("/api/v1/calendar").param("year", "2090"))
                .andExpect(jsonPath("$.events", empty()));
        mvc.perform(put("/api/v1/calendar-events/{id}", id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"description\":\"Description\",", "").replace("Company holiday", "Updated")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.description").value(nullValue()));
        mvc.perform(delete("/api/v1/calendar-events/{id}", id).header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/calendar-events/{id}", id)).andExpect(status().isNotFound());
        assertThat(actions(id)).containsExactly("CREATE", "UPDATE", "DELETE");
        assertThat(jdbc.queryForObject("SELECT after_data->>'version' FROM calendar_event_audit WHERE action='UPDATE'", String.class)).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT before_data->>'title' FROM calendar_event_audit WHERE action='DELETE'", String.class)).isEqualTo("Updated");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calendar_event_audit WHERE actor_user_id='hr-actor'", Integer.class)).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "admin-actor", roles = "ADMIN")
    void adminPublishesUpdatesWithReasonAndCancelsWithoutDeletingHistory() throws Exception {
        long id = create();
        mvc.perform(patch("/api/v1/calendar-events/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(header().string("ETag", "\"1\""));
        mvc.perform(get("/api/v1/calendar").param("year", "2090"))
                .andExpect(jsonPath("$.events[0].id").value(id));
        mvc.perform(put("/api/v1/calendar-events/{id}", id).header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/calendar-events/{id}", id).header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON).content(withReason(BODY, "Schedule updated")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""));
        mvc.perform(delete("/api/v1/calendar-events/{id}", id).header("If-Match", "\"2\""))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/calendar-events/{id}/cancel", id).header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Plans changed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(header().string("ETag", "\"3\""));
        mvc.perform(get("/api/v1/calendar").param("year", "2090"))
                .andExpect(jsonPath("$.events[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.events[0].createdBy").doesNotExist());
        mvc.perform(put("/api/v1/calendar-events/{id}", id).header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/calendar-events/{id}/publish", id).header("If-Match", "\"3\""))
                .andExpect(status().isConflict());
        assertThat(actions(id)).containsExactly("CREATE", "PUBLISH", "UPDATE", "CANCEL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calendar_event_audit WHERE actor_user_id='admin-actor'", Integer.class)).isEqualTo(4);
    }

    @Test
    void enforcesVersionOnAllExistingEventMutations() throws Exception {
        long id = create();
        var requests = List.of(
                put("/api/v1/calendar-events/{id}", id).contentType(MediaType.APPLICATION_JSON).content(BODY),
                delete("/api/v1/calendar-events/{id}", id),
                patch("/api/v1/calendar-events/{id}/publish", id),
                patch("/api/v1/calendar-events/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed\"}"));
        for (var request : requests) {
            mvc.perform(request).andExpect(status().is(428));
            mvc.perform(request.header("If-Match", "\"99\"")).andExpect(status().isPreconditionFailed());
        }
        for (String match : List.of("*", "W/\"0\"", "0", "\"0\",\"1\"", "\"-1\"", "\"999999999999999999999999\"")) {
            mvc.perform(delete("/api/v1/calendar-events/{id}", id).header("If-Match", match))
                    .andExpect(status().isBadRequest());
        }
        assertThat(actions(id)).containsExactly("CREATE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "type", "holidayKind", "allDay", "startDate", "endDate", "timezone", "audienceType"})
    void rejectsMissingRequiredHolidayFields(String field) throws Exception {
        var body = mapper.readTree(BODY).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) body).remove(field);
        mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calendar_events", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "status", "version", "createdBy", "updatedBy", "createdAt", "updatedAt", "unexpected"})
    void rejectsServerManagedAndUnknownFields(String field) throws Exception {
        var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        body.put(field, "forged");
        mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidRangesClassificationsAndEnums() throws Exception {
        for (String body : List.of(
                BODY.replace("2090-01-16", "2090-01-14"),
                BODY.replace("  Company holiday  ", " "),
                BODY.replace("COMPANY_DAY_OFF", "UNKNOWN"),
                BODY.replace("Asia/Ho_Chi_Minh", "UTC"),
                BODY.replace("ALL", "TEAM"),
                BODY.replace("HOLIDAY", "MEETING"),
                BODY.replace("\"allDay\":true", "\"allDay\":false"),
                BODY.replace("\"allDay\":true", "\"startAt\":\"2090-01-01T00:00:00Z\",\"allDay\":true"))) {
            mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void listsDraftsAndFiltersOverlappingTimedEventsWithPagination() throws Exception {
        create();
        String timed = """
                {"title":"Timed meeting","type":"MEETING","allDay":false,
                 "startAt":"2090-01-15T16:00:00Z","endAt":"2090-01-15T17:00:00Z",
                 "timezone":"Asia/Ho_Chi_Minh","audienceType":"ALL"}
                """;
        mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON).content(timed))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/calendar-events").param("from", "2090-01-15").param("to", "2090-01-15").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].title").value("Company holiday"));
        mvc.perform(get("/api/v1/calendar-events").param("from", "2090-01-15").param("to", "2090-01-15")
                        .param("type", "MEETING").param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Timed meeting"));
        mvc.perform(get("/api/v1/calendar-events").param("from", "2090-01-16").param("to", "2090-01-16").param("type", "MEETING"))
                .andExpect(jsonPath("$.content", empty()));
        mvc.perform(get("/api/v1/calendar-events").param("from", "2090-01-01").param("to", "2092-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prioritizesDraftsThenNearestVietnamStartDateBeforePagination() throws Exception {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var today = LocalDate.now(zone);
        insertForSorting("Published today", "PUBLISHED", today);
        insertForSorting("Draft far away", "DRAFT", today.plusDays(30));
        insertForSorting("Published upcoming", "PUBLISHED", today.plusDays(5));
        insertForSorting("Draft yesterday", "DRAFT", today.minusDays(1));
        insertForSorting("Cancelled nearby", "CANCELLED", today.minusDays(2));
        insertForSorting("Draft today", "DRAFT", today);
        // 00:30 Vietnam is still yesterday in UTC; it must rank with today's events.
        var start = today.atStartOfDay(zone).plusMinutes(30).toInstant();
        jdbc.update("""
                INSERT INTO calendar_events
                    (title, type, all_day, start_at, end_at, status, created_by, updated_by)
                VALUES ('Timed draft today', 'MEETING', false, ?::timestamptz, ?::timestamptz,
                    'DRAFT', 'test', 'test')
                """, start.toString(), start.plusSeconds(3600).toString());
        var expectedPages = List.of(
                List.of("Draft today", "Timed draft today"),
                List.of("Draft yesterday", "Draft far away"),
                List.of("Published today", "Cancelled nearby"),
                List.of("Published upcoming"));
        for (int page = 0; page < expectedPages.size(); page++) {
            mvc.perform(get("/api/v1/calendar-events")
                            .param("from", today.minusDays(10).toString())
                            .param("to", today.plusDays(40).toString())
                            .param("size", "2").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(7))
                    .andExpect(jsonPath("$.totalPages").value(4))
                    .andExpect(jsonPath("$.content[*].title", contains(expectedPages.get(page).toArray())));
        }
        mvc.perform(get("/api/v1/calendar-events")
                        .param("from", today.minusDays(10).toString())
                        .param("to", today.plusDays(40).toString())
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Published today", "Published upcoming")));
    }

    private void insertForSorting(String title, String status, LocalDate date) {
        jdbc.update("""
                INSERT INTO calendar_events
                    (title, type, all_day, start_date, end_date, status, created_by, updated_by)
                VALUES (?, 'COMPANY_EVENT', true, ?, ?, ?, 'test', 'test')
                """, title, date, date, status);
    }

    @Test
    void publishedEventsCannotChangeClassificationOrMoveIntoPast() throws Exception {
        long id = create();
        mvc.perform(patch("/api/v1/calendar-events/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isOk());
        for (String body : List.of(BODY.replace("COMPANY_DAY_OFF", "PUBLIC_HOLIDAY"), BODY.replace("2090-", "2000-"))) {
            mvc.perform(put("/api/v1/calendar-events/{id}", id).header("If-Match", "\"1\"")
                            .contentType(MediaType.APPLICATION_JSON).content(withReason(body, "Change")))
                    .andExpect(status().isConflict());
        }
        mvc.perform(get("/api/v1/calendar-events/{id}", id))
                .andExpect(jsonPath("$.startDate").value("2090-01-15"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(actions(id)).containsExactly("CREATE", "PUBLISH");
    }

    @Test
    void pastDraftCannotBePublishedAndStartedPublishedEventCannotBeCancelled() throws Exception {
        var result = mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("2090-", "2000-"))).andExpect(status().isCreated()).andReturn();
        long id = id(result);
        mvc.perform(patch("/api/v1/calendar-events/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE calendar_events SET status='PUBLISHED' WHERE id=?", id);
        mvc.perform(patch("/api/v1/calendar-events/{id}/cancel", id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Change\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void auditFailureRollsBackEventWrite() throws Exception {
        jdbc.execute("ALTER TABLE calendar_event_audit ADD CONSTRAINT test_reject_create CHECK (action <> 'CREATE')");
        try {
            mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM calendar_events", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE calendar_event_audit DROP CONSTRAINT test_reject_create");
        }
    }

    @Test
    void simultaneousUpdatesAllowOnlyOneWriterAndOneAuditEntry() throws Exception {
        long id = create();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> update = () -> {
                gate.await();
                return mvc.perform(put("/api/v1/calendar-events/{id}", id).with(user("parallel-hr").roles("HR"))
                                .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(BODY))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(update);
            var second = executor.submit(update);
            gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 412);
        }
        assertThat(actions(id)).containsExactly("CREATE", "UPDATE");
    }

    private long create() throws Exception {
        return id(mvc.perform(post("/api/v1/calendar-events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Location", startsWith("/api/v1/calendar-events/"))).andReturn());
    }

    private long id(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String withReason(String body, String reason) {
        return body.strip().replaceFirst("\\}$", ",\"reason\":\"" + reason + "\"}");
    }

    private List<String> actions(long id) {
        return jdbc.queryForList("SELECT action FROM calendar_event_audit WHERE calendar_event_id=? ORDER BY id", String.class, id);
    }
}
