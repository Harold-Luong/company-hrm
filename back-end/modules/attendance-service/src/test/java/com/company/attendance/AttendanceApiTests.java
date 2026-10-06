package com.company.attendance;

import com.company.attendance.config.AttendanceProperties;
import com.company.attendance.service.WorkforceClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AttendanceApiTests.Fixtures.class)
class AttendanceApiTests extends JwtTestSupport {
    static final UUID EMPLOYEE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    static final String BASE = "/api/v1/attendance";
    static final LocalDate DATE = LocalDate.of(2030, 1, 7);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeSource source;
    @Autowired MutableClock clock;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        String url = System.getenv("ATTENDANCE_TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ATTENDANCE_TEST_DB_USER", "attendance_test"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ATTENDANCE_TEST_DB_PASSWORD", ""));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @BeforeEach
    void reset() {
        for (String table : List.of("attendance_events", "attendance_daily", "work_schedule_rules", "attendance_schedule_batches", "attendance_operations", "shift_revisions", "work_shifts"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE attendance_schedule_state SET revision=0, version=0 WHERE id=1");
        source.leaves = List.of(); source.holidays = List.of(); source.fail = false; source.active = true;
        clock.at("07:00");
    }
    @Test
    void shiftPermissionsValidationVersionAndHistory() throws Exception {
        mvc.perform(post(BASE + "/shifts").with(employee()).contentType("application/json").content(shift("13:00", "15:00")))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/shifts").with(hr()).contentType("application/json").content(shift("15:00", "13:00")))
                .andExpect(status().isBadRequest());
        String id = createShift("13:00", "15:00");
        mvc.perform(put(BASE + "/shifts/" + id).with(hr()).contentType("application/json").content(shift("13:00", "15:30")))
                .andExpect(status().isPreconditionRequired());
        clock.at("07:01");
        mvc.perform(put(BASE + "/shifts/" + id).with(hr()).header("If-Match", "\"0\"")
                .contentType("application/json").content(shift("13:00", "15:30")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiredMinutes").value(150)).andExpect(header().string("ETag", "\"1\""));
        mvc.perform(delete(BASE + "/shifts/" + id).with(hr()).header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(get(BASE + "/shifts/" + id + "/history").with(hr()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test
    void selectedSchedulesExpireAndAllReplacesOverridesWithoutChangingPast() throws Exception {
        String global = createShift("13:30", "17:30"), personal = createShift("13:00", "15:00");
        apply(schedule(global, "COMPANY_DEFAULT", List.of(), DATE, null), "g", 0);
        apply(schedule(personal, "SELECTED_EMPLOYEES", List.of(EMPLOYEE), DATE, DATE), "p", 1);
        mvc.perform(get(BASE + "/schedules/mine?from=" + DATE + "&until=" + DATE.plusDays(7)).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requiredMinutes").value(120))
                .andExpect(jsonPath("$[7].requiredMinutes").value(240));
        apply(schedule(personal, "SELECTED_EMPLOYEES", List.of(OTHER), DATE, null), "p2", 2);
        apply(schedule(global, "ALL_EMPLOYEES", List.of(), DATE, DATE), "all", 3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_schedule_rules WHERE employee_id IS NOT NULL AND effective_from <= ? AND effective_until >= ?", Integer.class, DATE, DATE)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_schedule_batches", Integer.class)).isEqualTo(4);
    }
    @Test
    void previewVersionAndIdempotencyProtectBulkChanges() throws Exception {
        String id = createShift("13:00", "15:00");
        String body = schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null);
        mvc.perform(post(BASE + "/schedules/preview").with(hr()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""));
        String first = apply(body, "same", 0);
        assertThat(apply(body, "same", 0)).isEqualTo(first);
        mvc.perform(post(BASE + "/schedules/apply").with(hr()).header("If-Match", "\"0\"")
                .header("Idempotency-Key", "new").contentType("application/json").content(body))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post(BASE + "/schedules/apply").with(hr()).header("If-Match", "\"1\"")
                .header("Idempotency-Key", "same").contentType("application/json")
                .content(schedule(id, "ALL_EMPLOYEES", List.of(), DATE, null))).andExpect(status().isConflict());
    }
    @Test
    void halfDayLeaveAndLateArrivalUseSnapshotAndExportSameCountedMinutes() throws Exception {
        String body = """
                {"name":"Default","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh",
                 "intervals":[{"period":"MORNING","start":"08:00","end":"12:00"},{"period":"AFTERNOON","start":"13:30","end":"17:30"}],
                 "checkInFrom":"06:00","checkOutUntil":"22:00"}
                """;
        String id = mapper.readTree(mvc.perform(post(BASE + "/shifts").with(hr()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        apply(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null), "schedule", 0);
        source.leaves = List.of(new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE, DATE, "MORNING", "APPROVED", 1));
        clock.at("13:38");
        String original = punch("check-in", "in").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        clock.at("13:40");
        assertThat(punch("check-in", "in").andReturn().getResponse().getContentAsString()).isEqualTo(original);
        punch("check-in", "duplicate").andExpect(status().isConflict());
        clock.at("17:30");
        punch("check-out", "out").andExpect(status().isOk())
                .andExpect(jsonPath("$.annualLeaveMinutes").value(240)).andExpect(jsonPath("$.leaveDays").value(0.5))
                .andExpect(jsonPath("$.workedActualSeconds").value(13920)).andExpect(jsonPath("$.workMinutesCounted").value(225))
                .andExpect(jsonPath("$.roundedLateMinutes").value(15));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_events", Integer.class)).isEqualTo(2);
        mvc.perform(get(BASE + "/reports?employeeId=" + EMPLOYEE + "&from=" + DATE + "&until=" + DATE).with(hr()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].workMinutesCounted").value(225));
        String csv = mvc.perform(get(BASE + "/reports/export.csv?employeeId=" + EMPLOYEE + "&from=" + DATE + "&until=" + DATE).with(hr()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF").contains("\"225\"").contains("'" + "=EMP001").contains("DRAFT");
        mvc.perform(get(BASE + "/reports?from=" + DATE + "&until=" + DATE).with(employee())).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/employees/" + EMPLOYEE + "/days/" + DATE + "/events").with(other()))
                .andExpect(status().isForbidden());
    }
    @Test
    void networkFailuresEmployeeStatusAndMissingPunchNeverCreateFakeWork() throws Exception {
        String id = createShift("13:00", "15:00");
        apply(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null), "schedule", 0);
        clock.at("13:00");
        mvc.perform(post(BASE + "/check-in").with(employee()).with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                .header("X-Forwarded-For", "127.0.0.1").header("Idempotency-Key", "forged"))
                .andExpect(status().isForbidden());
        punch("check-out", "out").andExpect(status().isConflict());
        source.fail = true;
        punch("check-in", "failed").andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_daily", Integer.class)).isZero();
        source.fail = false; source.active = false;
        punch("check-in", "inactive").andExpect(status().isForbidden());
        source.active = true;
        punch("check-in", "in").andExpect(status().isOk()).andExpect(jsonPath("$.workMinutesCounted").doesNotExist());
        clock.at("23:00");
        mvc.perform(get(BASE + "/mine?from=" + DATE + "&until=" + DATE).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("MISSING_CHECK_OUT"))
                .andExpect(jsonPath("$[0].workedActualSeconds").doesNotExist());
    }
    @Test
    void approvedLeaveBlocksOnlyAffectedWeekdaysAndEmployeeOverrides() throws Exception {
        String id = createShift("13:00", "15:00");
        source.leaves = List.of(new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE.plusDays(1), DATE.plusDays(1), "FULL_DAY", "APPROVED", 1));
        apply(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null), "monday-only", 0);
        source.leaves = List.of();
        apply(schedule(id, "SELECTED_EMPLOYEES", List.of(EMPLOYEE), DATE, null), "override", 1);
        source.leaves = List.of(new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE, DATE, "FULL_DAY", "APPROVED", 1));
        apply(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null), "keep-override", 2);
        mvc.perform(post(BASE + "/schedules/preview").with(hr()).contentType("application/json")
                .content(schedule(id, "ALL_EMPLOYEES", List.of(), DATE, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvedLeaveConflicts").value(1));
    }
    @Test
    void refreshingCoverageRequiresVersionAndKeepsOriginalPunches() throws Exception {
        String id = createShift("13:00", "15:00");
        apply(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null), "schedule", 0);
        clock.at("13:00"); punch("check-in", "in").andExpect(status().isOk());
        clock.at("15:00"); punch("check-out", "out").andExpect(status().isOk());
        source.leaves = List.of(new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE, DATE, "FULL_DAY", "APPROVED", 1));
        mvc.perform(get(BASE + "/mine?from=" + DATE + "&until=" + DATE).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].workMinutesCounted").value(120));
        String path = BASE + "/employees/" + EMPLOYEE + "/days/" + DATE + "/refresh-coverage";
        mvc.perform(post(path).with(employee()).header("If-Match", "\"1\"")).andExpect(status().isForbidden());
        mvc.perform(post(path).with(hr()).header("If-Match", "\"0\"")).andExpect(status().isPreconditionFailed());
        clock.at("15:01");
        mvc.perform(post(path).with(hr()).header("If-Match", "\"1\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOURCE_CONFLICT"))
                .andExpect(jsonPath("$.workMinutesCounted").doesNotExist()).andExpect(jsonPath("$.recordVersion").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_events", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_operations WHERE operation_type='REFRESH_COVERAGE'", Integer.class)).isEqualTo(1);
    }
    @Test
    void concurrentCheckInsHaveOneWinnerAndScheduleChangesCannotRewriteRecordedDay() throws Exception {
        String id = createShift("13:00", "15:00");
        apply(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null), "schedule", 0); clock.at("13:00");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> first = () -> { gate.await(); return punch("check-in", "a").andReturn().getResponse().getStatus(); };
            Callable<Integer> second = () -> { gate.await(); return punch("check-in", "b").andReturn().getResponse().getStatus(); };
            var a = pool.submit(first); var b = pool.submit(second); gate.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        mvc.perform(post(BASE + "/schedules/apply").with(hr()).header("If-Match", "\"1\"")
                .header("Idempotency-Key", "change").contentType("application/json")
                .content(schedule(id, "COMPANY_DEFAULT", List.of(), DATE, null))).andExpect(status().isConflict());
    }
    private ResultActions punch(String type, String key) throws Exception {
        return mvc.perform(post(BASE + "/" + type).with(employee()).header("Idempotency-Key", key));
    }
    private String createShift(String from, String until) throws Exception {
        return mapper.readTree(mvc.perform(post(BASE + "/shifts").with(hr()).contentType("application/json").content(shift(from, until)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }
    private String shift(String from, String until) {
        return """
                {"name":"Test shift","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh",
                 "intervals":[{"period":"AFTERNOON","start":"%s","end":"%s"}],"checkInFrom":"06:00","checkOutUntil":"22:00"}
                """.formatted(from, until);
    }
    private String schedule(String shift, String scope, List<UUID> employees, LocalDate from, LocalDate until) {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("shiftId", shift); body.put("shiftVersion", 0);
        body.put("scope", scope); body.put("employeeIds", employees); body.put("from", from); body.put("until", until);
        body.put("weekdays", List.of("MONDAY")); body.put("reason", "Test assignment"); return mapper.writeValueAsString(body);
    }
    private String apply(String body, String key, long revision) throws Exception {
        return mvc.perform(post(BASE + "/schedules/apply").with(hr()).header("If-Match", "\"" + revision + "\"")
                .header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    static RequestPostProcessor employee() { return actor(EMPLOYEE, "EMPLOYEE"); }
    static RequestPostProcessor other() { return actor(OTHER, "EMPLOYEE"); }
    static RequestPostProcessor hr() { return actor(OTHER, "HR"); }
    static RequestPostProcessor actor(UUID id, String role) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("employee_id", id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
    static class MutableClock extends Clock {
        private volatile Instant time;
        void at(String value) { time = DATE.atTime(LocalTime.parse(value)).atZone(getZone()).toInstant(); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
        @Override public Instant instant() { return time; }
    }
    static class FakeSource extends WorkforceClient {
        volatile List<Leave> leaves = List.of(); volatile List<Holiday> holidays = List.of();
        volatile boolean fail; volatile boolean active = true;
        FakeSource(AttendanceProperties properties, ObjectMapper mapper) { super(properties, mapper); }
        @Override public Employee employee(UUID id, JwtAuthenticationToken actor) {
            if (fail) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Test source unavailable");
            return new Employee(id, "=EMP001", "An", "Nguyễn", active ? "ACTIVE" : "INACTIVE", DATE.minusYears(1), null);
        }
        @Override public List<Employee> employees(JwtAuthenticationToken actor) { return List.of(employee(EMPLOYEE, actor), employee(OTHER, actor)); }
        @Override public List<Leave> leaves(UUID employee, LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
            return leaves.stream().filter(l -> employee == null || employee.equals(l.employeeId())).toList();
        }
        @Override public List<Holiday> holidays(LocalDate from, LocalDate until, JwtAuthenticationToken actor) { return holidays; }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
        @Bean @Primary FakeSource source(AttendanceProperties properties, ObjectMapper mapper) { return new FakeSource(properties, mapper); }
    }
}
