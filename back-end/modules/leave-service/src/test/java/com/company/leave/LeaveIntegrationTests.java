package com.company.leave;

import com.company.leave.request.LeaveModels.*;
import com.company.leave.request.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Must point to a disposable, already initialized leave database. Clears only its Leave tables. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LeaveIntegrationTests.TimeConfig.class)
@EnabledIfEnvironmentVariable(named = "LEAVE_TEST_DB_URL", matches = ".+")
class LeaveIntegrationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LeaveService service;
    static final UUID EMPLOYEE = UUID.fromString("d38e31b7-0bba-420c-8eaf-50edbe5a61ae");
    static final UUID HR = UUID.fromString("aa8e31b7-0bba-420c-8eaf-50edbe5a61ae");
    static final String BASE = "/api/v1/leave/requests";
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("LEAVE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("LEAVE_TEST_DB_USER", "leave_test"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("LEAVE_TEST_DB_PASSWORD", ""));
    }
    @BeforeEach
    void clear() { jdbc.execute("TRUNCATE leave_request_history, leave_balances, leave_requests, leave_request_owners RESTART IDENTITY"); }
    private JwtAuthenticationToken actor(UUID employee, String role) {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(employee.toString())
                .claim("employee_id", employee.toString()).claim("roles", List.of(role)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private Submission body() { return new Submission(LeaveType.ANNUAL, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 3), LeavePeriod.FULL_DAY, "Family matters"); }
    private Request submit(UUID employee) { return service.submit(body(), actor(employee, "EMPLOYEE")); }

    @Test
    void completeEmployeeToHrFlowAndHistory() throws Exception {
        mvc.perform(post(BASE).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))).contentType("application/json")
                .content("""
                    {"leaveType":"ANNUAL","startDate":"2030-02-01","endDate":"2030-02-03","reason":"Family matters",
                     "employeeId":"aa8e31b7-0bba-420c-8eaf-50edbe5a61ae","status":"APPROVED"}
                    """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.employeeId").value(EMPLOYEE.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.period").value("FULL_DAY")).andExpect(jsonPath("$.totalUnits").value(6))
                .andExpect(header().string("ETag", "\"0\""));
        UUID id = jdbc.queryForObject("SELECT id FROM leave_requests", UUID.class);
        mvc.perform(get(BASE + "/pending-count").with(authentication(actor(HR, "HR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(get(BASE + "/inbox?status=PENDING").with(authentication(actor(HR, "HR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(patch(BASE + "/" + id + "/approve").with(authentication(actor(HR, "HR")))
                .header("If-Match", "\"0\"").contentType("application/json").content("{\"note\":\"Agreed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(BASE + "/mine").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(jsonPath("$.content[0].reviewNote").value("Agreed"));
        mvc.perform(get(BASE + "/" + id + "/history").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(jsonPath("$[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].action").value("APPROVED"));
        mvc.perform(get(BASE + "/balance?year=2030").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entitledDays").value(12))
                .andExpect(jsonPath("$.usedDays").value(3)).andExpect(jsonPath("$.remainingDays").value(9));
    }

    @Test
    void supportsDifferentHalfDaysButRejectsTheSameHalfDay() throws Exception {
        LocalDate date = LocalDate.of(2030, 3, 1);
        service.submit(new Submission(LeaveType.ANNUAL, date, date, LeavePeriod.MORNING, "Appointment"),
                actor(EMPLOYEE, "EMPLOYEE"));
        assertThat(service.submit(new Submission(LeaveType.ANNUAL, date, date, LeavePeriod.AFTERNOON, "Family"),
                actor(EMPLOYEE, "EMPLOYEE")).totalUnits()).isEqualTo(1);
        assertThatThrownBy(() -> service.submit(
                new Submission(LeaveType.ANNUAL, date, date, LeavePeriod.MORNING, "Duplicate"),
                actor(EMPLOYEE, "EMPLOYEE"))).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409);
    }
    @Test
    void ownershipAndSelfApprovalAreEnforced() throws Exception {
        var request = submit(EMPLOYEE);
        mvc.perform(get(BASE + "/mine").with(authentication(actor(HR, "EMPLOYEE"))))
                .andExpect(jsonPath("$.totalElements").value(0));
        for (String suffix : List.of("", "/history")) {
            mvc.perform(get(BASE + "/" + request.id() + suffix).with(authentication(actor(HR, "EMPLOYEE"))))
                    .andExpect(status().isNotFound());
        }
        for (String action : List.of("approve", "reject")) {
            mvc.perform(patch(BASE + "/" + request.id() + "/" + action).with(authentication(actor(EMPLOYEE, "HR")))
                    .header("If-Match", "\"0\"").contentType("application/json").content("{\"note\":\"Self review\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch(BASE + "/" + request.id() + "/cancel").with(authentication(actor(HR, "HR")))
                .header("If-Match", "\"0\"")).andExpect(status().isForbidden());
    }
    @Test
    void rejectsInvalidFormsPaginationAndMissingVersion() throws Exception {
        for (String body : List.of("{}",
                "{\"leaveType\":\"ANNUAL\",\"startDate\":\"2029-01-01\",\"endDate\":\"2030-01-01\",\"reason\":\"reason\"}",
                "{\"leaveType\":\"ANNUAL\",\"startDate\":\"2030-02-02\",\"endDate\":\"2030-02-01\",\"reason\":\"reason\"}",
                "{\"leaveType\":\"ANNUAL\",\"startDate\":\"2030-02-01\",\"endDate\":\"2030-02-02\",\"reason\":\" \"}")) {
            mvc.perform(post(BASE).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(BASE + "/mine?size=101").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isBadRequest());
        var request = submit(EMPLOYEE);
        mvc.perform(patch(BASE + "/" + request.id() + "/approve").with(authentication(actor(HR, "HR")))
                .contentType("application/json").content("{}"))
                .andExpect(status().isPreconditionRequired());
    }
    @Test
    void rejectionRequiresReasonAndCancellationAllowsResubmission() throws Exception {
        var request = submit(EMPLOYEE);
        mvc.perform(patch(BASE + "/" + request.id() + "/reject").with(authentication(actor(HR, "ADMIN")))
                .header("If-Match", "\"0\"").contentType("application/json").content("{\"note\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/" + request.id() + "/cancel").with(authentication(actor(EMPLOYEE, "EMPLOYEE")))
                .header("If-Match", "\"0\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        var second = submit(EMPLOYEE);
        service.decide(second.id(), Status.REJECTED, "\"0\"", "Please change dates", actor(HR, "HR"));
        assertThat(service.get(second.id(), actor(EMPLOYEE, "EMPLOYEE")).reviewNote()).isEqualTo("Please change dates");
        assertThat(submit(EMPLOYEE).status()).isEqualTo(Status.PENDING);
    }
    @Test
    void staleVersionAndFinalStateCannotBeOverwritten() throws Exception {
        var request = submit(EMPLOYEE);
        service.decide(request.id(), Status.APPROVED, "\"0\"", null, actor(HR, "HR"));
        mvc.perform(patch(BASE + "/" + request.id() + "/reject").with(authentication(actor(HR, "HR")))
                .header("If-Match", "\"0\"").contentType("application/json").content("{\"note\":\"Changed mind\"}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(patch(BASE + "/" + request.id() + "/cancel").with(authentication(actor(EMPLOYEE, "EMPLOYEE")))
                .header("If-Match", "\"1\""))
                .andExpect(status().isConflict());
    }
    @Test
    void overlappingConcurrentSubmissionsOnlyCreateOneRequest() throws Exception {
        var outcomes = parallel(() -> { submit(EMPLOYEE); return 201; }, () -> { submit(EMPLOYEE); return 201; });
        assertThat(outcomes).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_requests", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request_history", Long.class)).isEqualTo(1);
    }
    @Test
    void concurrentDecisionsHaveOneWinnerAndOneAudit() throws Exception {
        var request = submit(EMPLOYEE);
        var outcomes = parallel(
                () -> { service.decide(request.id(), Status.APPROVED, "\"0\"", null, actor(HR, "HR")); return 200; },
                () -> { service.decide(request.id(), Status.REJECTED, "\"0\"", "Declined", actor(HR, "ADMIN")); return 200; });
        assertThat(outcomes).containsExactlyInAnyOrder(200, 412);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request_history", Long.class)).isEqualTo(2);
    }
    @Test
    void auditFailureRollsBackRequest() {
        jdbc.execute("ALTER TABLE leave_request_history ADD CONSTRAINT test_reject_audit CHECK (action <> 'SUBMITTED')");
        try {
            assertThatThrownBy(() -> submit(EMPLOYEE)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_requests", Long.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE leave_request_history DROP CONSTRAINT test_reject_audit"); }
    }
    private List<Integer> parallel(Callable<Integer> first, Callable<Integer> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var jobs = List.of(first, second).stream().map(job -> pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { return job.call(); } catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            })).toList();
            return List.of(jobs.get(0).get(20, TimeUnit.SECONDS), jobs.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
