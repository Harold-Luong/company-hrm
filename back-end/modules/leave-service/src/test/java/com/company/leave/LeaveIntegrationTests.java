package com.company.leave;

import com.company.leave.request.LeaveModels.*;
import com.company.leave.request.LeaveService;
import com.company.leave.request.LeaveHistoryAction;
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
    @Autowired com.company.leave.request.LeaveAdjustmentService adjustments;
    @Autowired FakeCalendar calendar;
    static final UUID EMPLOYEE = UUID.fromString("d38e31b7-0bba-420c-8eaf-50edbe5a61ae");
    static final UUID HR = UUID.fromString("aa8e31b7-0bba-420c-8eaf-50edbe5a61ae");
    static final String BASE = "/api/v1/leave/requests";
    static class FakeCalendar extends com.company.leave.request.LeaveCalendarClient {
        boolean workingWeek;
        boolean fail;
        boolean changed;
        FakeCalendar() { super("http://unused", new tools.jackson.databind.ObjectMapper()); }
        @Override public List<Day> calendar(UUID employee, LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
            if (fail) throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Working calendar unavailable");
            return from.datesUntil(until.plusDays(1)).map(d -> {
                boolean off = workingWeek && (d.getDayOfWeek().getValue() >= 6 || d.equals(LocalDate.of(2030, 2, 4)));
                return new Day(d, new UUID(0, 1), changed ? 1L : 0L, off ? 0 : 2, off ? 0 : 1, off ? 0 : 1);
            }).toList();
        }
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary FakeCalendar fakeCalendar() { return new FakeCalendar(); }
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("LEAVE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("LEAVE_TEST_DB_USER", "leave_test"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("LEAVE_TEST_DB_PASSWORD", ""));
    }
    @BeforeEach
    void clear() { calendar.workingWeek = false; calendar.fail = false; calendar.changed = false; jdbc.execute("TRUNCATE leave_calendar_snapshots, leave_adjustments, leave_request_history, leave_balances, leave_requests, leave_request_owners RESTART IDENTITY"); }
    private JwtAuthenticationToken actor(UUID employee, String role) {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(employee.toString())
                .claim("employee_id", employee.toString()).claim("roles", List.of(role)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private Submission body() { return new Submission(LeaveType.ANNUAL, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 3), LeavePeriod.FULL_DAY, "Family matters"); }
    private Request submit(UUID employee) { return service.submit(body(), actor(employee, "EMPLOYEE")); }

    private Request approved() {
        var request = submit(EMPLOYEE);
        return service.decide(request.id(), Status.APPROVED, "\"0\"", null, actor(HR, "HR"));
    }
    private String version(Request request) { return "\"" + request.version() + "\""; }
    private AdjustmentSubmission cancellation() { return new AdjustmentSubmission(true, null, null, null, "Worked as usual"); }

    @Test
    void workingCalendarExcludesWeekendAndHolidayAndRefundUsesOriginalBasis() {
        calendar.workingWeek = true;
        var body = new Submission(LeaveType.ANNUAL, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 5), LeavePeriod.FULL_DAY, "Family");
        var request = service.submit(body, actor(EMPLOYEE, "EMPLOYEE"));
        assertThat(request.totalUnits()).isEqualTo(4); // Friday + Tuesday, Monday is a holiday.
        var approved = service.decide(request.id(), Status.APPROVED, version(request), null, actor(HR, "HR"));
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(4);
        var day = LocalDate.of(2030, 2, 5);
        var pending = adjustments.submit(request.id(), version(approved),
            new AdjustmentSubmission(false, day, day, LeavePeriod.AFTERNOON, "Keep only Tuesday afternoon"), actor(EMPLOYEE, "EMPLOYEE"));
        calendar.changed = true; calendar.workingWeek = false; // Refund must not use a newer calendar.
        var change = adjustments.list(request.id(), actor(HR, "HR")).getFirst();
        var result = adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, null, actor(HR, "HR"));
        assertThat(result.totalUnits()).isEqualTo(1);
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(1);
    }

    @Test
    void noWorkingDaysUnavailableSourceAndCalendarChangesNeverConsumeBalance() {
        calendar.workingWeek = true;
        var off = new Submission(LeaveType.ANNUAL, LocalDate.of(2030, 2, 2), LocalDate.of(2030, 2, 4), LeavePeriod.FULL_DAY, "Off days");
        assertThatThrownBy(() -> service.submit(off, actor(EMPLOYEE, "EMPLOYEE")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        calendar.fail = true;
        assertThatThrownBy(() -> submit(EMPLOYEE)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_requests", Integer.class)).isZero();
        calendar.fail = false;
        var request = submit(EMPLOYEE);
        calendar.changed = true;
        assertThatThrownBy(() -> service.decide(request.id(), Status.APPROVED, version(request), null, actor(HR, "HR")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isZero();
        assertThat(service.get(request.id(), actor(EMPLOYEE, "EMPLOYEE")).status()).isEqualTo(Status.PENDING);
    }

    @Test
    void estimateUsesHalfDayUnitsAndLegacyPendingRequiresResubmission() throws Exception {
        calendar.workingWeek = true;
        mvc.perform(get(BASE + "/estimate?from=2030-02-01&until=2030-02-01&period=MORNING").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalUnits").value(1)).andExpect(jsonPath("$.totalDays").value(0.5));
        var request = submit(EMPLOYEE);
        jdbc.update("DELETE FROM leave_calendar_snapshots WHERE request_id = ?", request.id());
        assertThatThrownBy(() -> service.decide(request.id(), Status.APPROVED, version(request), null, actor(HR, "HR")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        service.decide(request.id(), Status.CANCELLED, version(request), null, actor(EMPLOYEE, "EMPLOYEE"));
    }

    @Test
    void approvedCancellationRefundsOnlyAfterReviewAndOnlyOnce() throws Exception {
        var request = approved();
        var pending = adjustments.submit(request.id(), version(request), cancellation(), actor(EMPLOYEE, "EMPLOYEE"));
        assertThat(pending.status()).isEqualTo(Status.APPROVED);
        assertThat(pending.adjustmentPending()).isTrue();
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(6);
        mvc.perform(get(BASE + "/adjustments/inbox").with(authentication(actor(HR, "HR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(BASE + "/pending-count").with(authentication(actor(HR, "HR"))))
                .andExpect(jsonPath("$.count").value(1));
        var change = adjustments.list(request.id(), actor(EMPLOYEE, "EMPLOYEE")).getFirst();
        var result = adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, "Verified", actor(HR, "HR"));
        assertThat(result.status()).isEqualTo(Status.CANCELLED);
        assertThat(result.adjustmentPending()).isFalse();
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isZero();
        assertThatThrownBy(() -> adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, null, actor(HR, "HR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(412));
        mvc.perform(get(BASE + "/calendar?year=2030").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reductionRetainsHalfDayAndRefundsDifference() throws Exception {
        var request = approved();
        var day = LocalDate.of(2030, 2, 2);
        var pending = adjustments.submit(request.id(), version(request), new AdjustmentSubmission(false, day, day, LeavePeriod.AFTERNOON, "Only afternoon"), actor(EMPLOYEE, "EMPLOYEE"));
        var change = adjustments.list(request.id(), actor(HR, "HR")).getFirst();
        var result = adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, null, actor(HR, "HR"));
        assertThat(result.status()).isEqualTo(Status.APPROVED);
        assertThat(result.totalUnits()).isEqualTo(1);
        assertThat(result.period()).isEqualTo(LeavePeriod.AFTERNOON);
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(1);
        mvc.perform(get(BASE + "/attendance?from=2030-02-01&until=2030-02-03").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(jsonPath("$[0].startDate").value("2030-02-02"))
                .andExpect(jsonPath("$[0].period").value("AFTERNOON"));
    }

    @Test
    void adjustmentOwnershipSelfReviewAndRejectionAreEnforced() throws Exception {
        var request = approved();
        assertThatThrownBy(() -> adjustments.submit(request.id(), version(request), cancellation(), actor(HR, "HR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        var pending = adjustments.submit(request.id(), version(request), cancellation(), actor(EMPLOYEE, "HR"));
        var change = adjustments.list(request.id(), actor(HR, "HR")).getFirst();
        assertThatThrownBy(() -> adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, null, actor(EMPLOYEE, "HR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        mvc.perform(patch(BASE + "/" + request.id() + "/adjustments/" + change.id() + "/approve")
                .header("If-Match", version(pending)).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + request.id() + "/adjustments").with(authentication(actor(UUID.randomUUID(), "EMPLOYEE"))))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> adjustments.decide(request.id(), change.id(), version(pending), Status.REJECTED, " ", actor(HR, "HR")))
                .isInstanceOf(ResponseStatusException.class);
        var rejected = adjustments.decide(request.id(), change.id(), version(pending), Status.REJECTED, "Still absent", actor(HR, "HR"));
        assertThat(rejected.status()).isEqualTo(Status.APPROVED);
        assertThat(rejected.adjustmentPending()).isFalse();
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(6);
        var second = adjustments.submit(request.id(), version(rejected), cancellation(), actor(EMPLOYEE, "EMPLOYEE"));
        var secondChange = adjustments.list(request.id(), actor(EMPLOYEE, "EMPLOYEE")).stream().filter(a -> a.status() == Status.PENDING).findFirst().orElseThrow();
        adjustments.decide(request.id(), secondChange.id(), version(second), Status.CANCELLED, null, actor(EMPLOYEE, "EMPLOYEE"));
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(6);
    }

    @Test
    void invalidReductionAndDuplicatePendingCannotChangeBalance() {
        var request = approved();
        for (var body : List.of(
                new AdjustmentSubmission(false, request.startDate(), request.endDate(), LeavePeriod.FULL_DAY, "Unchanged"),
                new AdjustmentSubmission(false, request.startDate().minusDays(1), request.startDate(), LeavePeriod.FULL_DAY, "Outside"),
                new AdjustmentSubmission(false, request.startDate(), request.endDate(), LeavePeriod.MORNING, "Multiple half days"))) {
            assertThatThrownBy(() -> adjustments.submit(request.id(), version(request), body, actor(EMPLOYEE, "EMPLOYEE")))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        var pending = adjustments.submit(request.id(), version(request), cancellation(), actor(EMPLOYEE, "EMPLOYEE"));
        assertThatThrownBy(() -> adjustments.submit(request.id(), version(pending), cancellation(), actor(EMPLOYEE, "EMPLOYEE")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(6);
    }

    @Test
    void concurrentAdjustmentApprovalsRefundOnlyOnce() throws Exception {
        var request = approved();
        var pending = adjustments.submit(request.id(), version(request), cancellation(), actor(EMPLOYEE, "EMPLOYEE"));
        var change = adjustments.list(request.id(), actor(HR, "HR")).getFirst();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> approve = () -> {
                start.await();
                try {
                    adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, null, actor(HR, "HR"));
                    return 200;
                } catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            };
            var first = pool.submit(approve);
            var second = pool.submit(approve);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 412);
        }
        assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isZero();
        assertThat(service.history(request.id(), actor(HR, "HR")).stream().filter(h -> h.action() == LeaveHistoryAction.ADJUSTMENT_APPROVED)).hasSize(1);
    }

    @Test
    void unpaidAdjustmentDoesNotRefundAnnualAndAnnualRefundUsesOriginalYear() {
        for (var type : List.of(LeaveType.UNPAID, LeaveType.ANNUAL)) {
            var day = LocalDate.of(2031, 2, type == LeaveType.UNPAID ? 1 : 2);
            var submitted = service.submit(new Submission(type, day, day, LeavePeriod.FULL_DAY, "Leave"), actor(EMPLOYEE, "EMPLOYEE"));
            var approved = service.decide(submitted.id(), Status.APPROVED, version(submitted), null, actor(HR, "HR"));
            var pending = adjustments.submit(approved.id(), version(approved), cancellation(), actor(EMPLOYEE, "EMPLOYEE"));
            var change = adjustments.list(approved.id(), actor(HR, "HR")).getFirst();
            adjustments.decide(approved.id(), change.id(), version(pending), Status.APPROVED, null, actor(HR, "HR"));
            assertThat(service.balance(2031, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isZero();
            assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isZero();
        }
    }

    @Test
    void adjustmentHttpContractRequiresVersionAndReturnsUpdatedEtag() throws Exception {
        var request = approved();
        String path = BASE + "/" + request.id() + "/adjustments";
        String json = "{\"cancelAll\":true,\"reason\":\"Worked normally\"}";
        mvc.perform(post(path).contentType("application/json").content(json).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post(path).header("If-Match", version(request)).contentType("application/json").content(json)
                .with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.adjustmentPending").value(true));
        var change = adjustments.list(request.id(), actor(HR, "HR")).getFirst();
        mvc.perform(patch(path + "/" + change.id() + "/approve").header("If-Match", "\"2\"")
                .contentType("application/json").content("{\"note\":\"Verified\"}").with(authentication(actor(HR, "HR"))))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void adjustmentAuditFailureRollsBackRefundAndDecision() {
        var request = approved();
        var pending = adjustments.submit(request.id(), version(request), cancellation(), actor(EMPLOYEE, "EMPLOYEE"));
        var change = adjustments.list(request.id(), actor(HR, "HR")).getFirst();
        jdbc.execute("ALTER TABLE leave_request_history ADD CONSTRAINT test_reject_adjustment_audit CHECK (action <> 'ADJUSTMENT_APPROVED')");
        try {
            assertThatThrownBy(() -> adjustments.decide(request.id(), change.id(), version(pending), Status.APPROVED, null, actor(HR, "HR")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(service.get(request.id(), actor(HR, "HR"))).isEqualTo(pending);
            assertThat(adjustments.list(request.id(), actor(HR, "HR")).getFirst().status()).isEqualTo(Status.PENDING);
            assertThat(service.balance(2030, actor(EMPLOYEE, "EMPLOYEE")).usedUnits()).isEqualTo(6);
        } finally { jdbc.execute("ALTER TABLE leave_request_history DROP CONSTRAINT test_reject_adjustment_audit"); }
    }

    @Test
    void personalCalendarAlwaysUsesTokenOwnerEvenForReviewersAndIgnoresSpoofedOwners() throws Exception {
        var own = submit(EMPLOYEE);
        var other = submit(HR);
        for (String role : List.of("EMPLOYEE", "MANAGER", "HR", "ADMIN")) {
            mvc.perform(get(BASE + "/calendar?year=2030&employeeId=" + HR + "&inbox=true")
                    .with(authentication(actor(EMPLOYEE, role))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(own.id().toString()))
                    .andExpect(jsonPath("$[0].employeeId").value(EMPLOYEE.toString()))
                    .andExpect(jsonPath("$[0].reason").doesNotExist())
                    .andExpect(jsonPath("$[0].reviewNote").doesNotExist());
        }
        mvc.perform(get(BASE + "/calendar?year=2030").with(authentication(actor(HR, "HR"))))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other.id().toString()));
        service.decide(own.id(), Status.APPROVED, "\"0\"", null, actor(HR, "HR"));
        mvc.perform(get(BASE + "/calendar?year=2030").with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
        service.decide(other.id(), Status.CANCELLED, "\"0\"", null, actor(HR, "HR"));
        mvc.perform(get(BASE + "/calendar?year=2030").with(authentication(actor(HR, "HR"))))
                .andExpect(jsonPath("$.length()").value(0));
        var rejected = submit(HR);
        service.decide(rejected.id(), Status.REJECTED, "\"0\"", "Not available", actor(EMPLOYEE, "HR"));
        mvc.perform(get(BASE + "/calendar?year=2030").with(authentication(actor(HR, "HR"))))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void personalCalendarIncludesWholeYearHalfDaysAndCrossYearBoundaries() throws Exception {
        for (int day = 2; day <= 23; day++) {
            LocalDate date = LocalDate.of(2030, 1, day);
            service.submit(new Submission(LeaveType.UNPAID, date, date, LeavePeriod.MORNING, "Appointment"),
                    actor(EMPLOYEE, "EMPLOYEE"));
        }
        var crossYear = service.submit(new Submission(LeaveType.UNPAID, LocalDate.of(2030,12,31),
                LocalDate.of(2031,1,2), LeavePeriod.FULL_DAY, "Travel"), actor(EMPLOYEE,"EMPLOYEE"));
        mvc.perform(get(BASE + "/calendar?year=2030").with(authentication(actor(EMPLOYEE,"EMPLOYEE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(23))
                .andExpect(jsonPath("$[0].period").value("MORNING"));
        mvc.perform(get(BASE + "/calendar?year=2031").with(authentication(actor(EMPLOYEE,"EMPLOYEE"))))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(crossYear.id().toString()))
                .andExpect(jsonPath("$[0].startDate").value("2030-12-31"))
                .andExpect(jsonPath("$[0].endDate").value("2031-01-02"));
        mvc.perform(get(BASE + "/calendar?year=2032").with(authentication(actor(EMPLOYEE,"EMPLOYEE"))))
                .andExpect(jsonPath("$.length()").value(0));
        for (String query : List.of("", "?year=1999", "?year=10000", "?year=invalid")) {
            mvc.perform(get(BASE + "/calendar" + query).with(authentication(actor(EMPLOYEE,"EMPLOYEE"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void firstBalanceReadInitializesAnEmployeeWithoutLeaveRequests() throws Exception {
        for (String path : List.of(BASE + "/balance", BASE + "/balance?year=2030")) {
            mvc.perform(get(path).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE.toString()))
                    .andExpect(jsonPath("$.year").value(2030))
                    .andExpect(jsonPath("$.entitledDays").value(12))
                    .andExpect(jsonPath("$.usedDays").value(0))
                    .andExpect(jsonPath("$.remainingDays").value(12));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request_owners", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_balances", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_requests", Integer.class)).isZero();
    }

    @Test
    void invalidBalanceYearDoesNotInitializeAnyLeaveData() throws Exception {
        for (int year : List.of(1999, 10000)) {
            mvc.perform(get(BASE + "/balance?year=" + year).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request_owners", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_balances", Integer.class)).isZero();
    }

    @Test
    void attendanceCoverageIsOwnerScopedAndOmitsPrivateReasons() throws Exception {
        var own = submit(EMPLOYEE); submit(HR);
        service.decide(own.id(), Status.APPROVED, "\"0\"", null, actor(HR, "HR"));
        String path = BASE + "/attendance?from=2030-02-01&until=2030-02-03";
        mvc.perform(get(path).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(EMPLOYEE.toString()))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].reason").doesNotExist())
                .andExpect(jsonPath("$[0].reviewNote").doesNotExist());
        mvc.perform(get(path + "&employeeId=" + HR).with(authentication(actor(EMPLOYEE, "EMPLOYEE"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(authentication(actor(HR, "HR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

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
