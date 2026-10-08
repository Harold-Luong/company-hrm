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
    @Autowired com.company.attendance.service.DefaultScheduleInitializer defaultSchedule;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired com.company.attendance.service.OperationService operationService;

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
        for (String table : List.of("attendance_overtime_requests", "attendance_request_history", "attendance_requests", "attendance_events", "attendance_daily", "work_schedule_rules", "attendance_schedule_batches", "attendance_operations", "shift_revisions", "work_shifts"))
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

    @Test
    void requestLifecycleIsPersistentOwnerScopedVersionedAndAudited() throws Exception {
        String shift = assignedRequestShift();
        String body = permissionBody(shift,"LATE_ARRIVAL","13:08");
        String first = submitPermission(body,"request-key",employee()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedMinutes").value(8)).andExpect(jsonPath("$.roundedRequestedMinutes").value(15))
                .andExpect(header().string("ETag","\"0\"")).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(first).get("id").asString();
        assertThat(submitPermission(body,"request-key",employee()).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        submitPermission(body,"duplicate",employee()).andExpect(status().isConflict());
        mvc.perform(get(BASE+"/requests").with(other())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(BASE+"/requests/"+id).with(other())).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/requests/"+id+"/history").with(other())).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/requests/inbox").with(employee())).andExpect(status().isForbidden());
        mvc.perform(get(BASE+"/requests/inbox").with(hr())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put(BASE+"/requests/"+id).with(hr()).header("If-Match","\"0\"").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(BASE+"/requests/"+id).with(employee()).contentType("application/json").content(body)).andExpect(status().isPreconditionRequired());
        mvc.perform(put(BASE+"/requests/"+id).with(employee()).header("If-Match","\"0\"").contentType("application/json")
                .content(permissionBody(shift,"LATE_ARRIVAL","13:16"))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post(BASE+"/requests/"+id+"/cancel").with(employee()).header("If-Match","\"0\"")).andExpect(status().isPreconditionFailed());
        mvc.perform(post(BASE+"/requests/"+id+"/cancel").with(employee()).header("If-Match","\"1\"")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get(BASE+"/requests/"+id+"/history").with(employee())).andExpect(jsonPath("$.totalElements").value(3));
        submitPermission(body,"replacement",employee()).andExpect(status().isCreated());
    }
    @Test
    void approvedRequestExplainsActualDeviationWithoutAddingWorkOrConsumingLeave() throws Exception {
        String shift=assignedRequestShift();
        String id=permissionId(submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08"),"request",employee()));
        clock.at("13:16");punch("check-in","in").andExpect(status().isOk());
        clock.at("15:00");punch("check-out","out").andExpect(status().isOk());
        decidePermission(id,"APPROVED",null,hr()).andExpect(status().isOk());
        mvc.perform(get(BASE+"/mine?from="+DATE+"&until="+DATE).with(employee())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workMinutesCounted").value(90)).andExpect(jsonPath("$[0].workedActualSeconds").value(6240))
                .andExpect(jsonPath("$[0].leaveDays").value(0)).andExpect(jsonPath("$[0].roundedLateMinutes").value(30))
                .andExpect(jsonPath("$[0].permissionCoverage.approvedRequestIds[0]").value(id))
                .andExpect(jsonPath("$[0].permissionCoverage.coveredLateSeconds").value(480))
                .andExpect(jsonPath("$[0].permissionCoverage.unapprovedRoundedLateMinutes").value(15));
        String csv=mvc.perform(get(BASE+"/reports/export.csv?employeeId="+EMPLOYEE+"&from="+DATE+"&until="+DATE).with(hr()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("approved_request_ids",id,"\"90\"").doesNotContain("Private request reason");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_events",Integer.class)).isEqualTo(2);
        mvc.perform(post(BASE+"/requests/"+id+"/cancel").with(employee()).header("If-Match","\"1\"")).andExpect(status().isConflict());
        source.leaves=List.of(new WorkforceClient.Leave(UUID.randomUUID(),EMPLOYEE,"ANNUAL",DATE,DATE,"AFTERNOON","APPROVED",1));
        clock.at("15:01");
        mvc.perform(post(BASE+"/employees/"+EMPLOYEE+"/days/"+DATE+"/refresh-coverage").with(hr()).header("If-Match","\"1\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissionCoverage.conflictingRequestIds[0]").value(id));
    }
    @Test
    void noSelfReviewAndRejectionRequiresReason() throws Exception {
        String shift=assignedRequestShift();
        String own=permissionId(submitPermission(permissionBody(shift,"EARLY_DEPARTURE","14:45"),"own",hr()));
        decidePermission(own,"APPROVED",null,hr()).andExpect(status().isForbidden());
        String id=permissionId(submitPermission(permissionBody(shift,"EARLY_DEPARTURE","14:45"),"employee",employee()));
        decidePermission(id,"APPROVED",null,employee()).andExpect(status().isForbidden());
        decidePermission(id,"CANCELLED",null,hr()).andExpect(status().isBadRequest());
        decidePermission(id,"REJECTED"," ",hr()).andExpect(status().isBadRequest());
        decidePermission(id,"REJECTED","Cần bàn giao",hr()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        decidePermission(id,"APPROVED",null,hr()).andExpect(status().isPreconditionFailed());
    }
    @Test
    void requestValidatesRealScheduleLeaveHolidaysAndServerDerivedDuration() throws Exception {
        String shift=assignedRequestShift();
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:00"),"boundary",employee()).andExpect(status().isBadRequest());
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08:01"),"seconds",employee()).andExpect(status().isBadRequest());
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08").replace("AFTERNOON","MORNING"),"period",employee()).andExpect(status().isBadRequest());
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08").replace(DATE.toString(),DATE.minusDays(1).toString()),"past",employee()).andExpect(status().isBadRequest());
        source.holidays=List.of(new WorkforceClient.Holiday(1L,"HOLIDAY","PUBLISHED","ALL",true,DATE,DATE));
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08"),"holiday",employee()).andExpect(status().isConflict());source.holidays=List.of();
        source.leaves=List.of(new WorkforceClient.Leave(UUID.randomUUID(),EMPLOYEE,"ANNUAL",DATE,DATE,"AFTERNOON","PENDING",0));
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08"),"leave",employee()).andExpect(status().isConflict());source.leaves=List.of();
        submitPermission(permissionBody(shift,"LATE_ARRIVAL","14:00"),"late",employee()).andExpect(status().isCreated());
        submitPermission(permissionBody(shift,"EARLY_DEPARTURE","14:00"),"whole",employee()).andExpect(status().isConflict());
    }
    @Test
    void changedAssignmentMakesPendingApprovalStaleAndApprovedRequestBlocksReassignment() throws Exception {
        String shift=assignedRequestShift();
        String id=permissionId(submitPermission(permissionBody(shift,"LATE_ARRIVAL","13:08"),"request",employee()));
        String different=createShift("13:00","15:30");
        apply(schedule(different,"COMPANY_DEFAULT",List.of(),DATE,null),"new-schedule",1);
        decidePermission(id,"APPROVED",null,hr()).andExpect(status().isPreconditionFailed());
        mvc.perform(put(BASE+"/requests/"+id).with(employee()).header("If-Match","\"0\"").contentType("application/json")
                .content(permissionBody(different,"LATE_ARRIVAL","13:08"))).andExpect(status().isOk());
        mvc.perform(post(BASE+"/requests/"+id+"/decision").with(hr()).header("If-Match","\"1\"").contentType("application/json")
                .content("{\"status\":\"APPROVED\"}")).andExpect(status().isOk());
        mvc.perform(post(BASE+"/schedules/preview").with(hr()).contentType("application/json").content(schedule(shift,"COMPANY_DEFAULT",List.of(),DATE,null)))
                .andExpect(jsonPath("$.approvedRequestConflicts").value(1));
        mvc.perform(post(BASE+"/schedules/apply").with(hr()).header("If-Match","\"2\"").header("Idempotency-Key","blocked")
                .contentType("application/json").content(schedule(shift,"COMPANY_DEFAULT",List.of(),DATE,null))).andExpect(status().isConflict());
    }
    @Test
    void concurrentPermissionSubmissionAndReviewHaveOnlyOneWinner() throws Exception {
        String shift=assignedRequestShift(),body=permissionBody(shift,"LATE_ARRIVAL","13:08");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return submitPermission(body,"a",employee()).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();return submitPermission(body,"b",employee()).andReturn().getResponse().getStatus();});gate.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            String id=jdbc.queryForObject("SELECT id FROM attendance_requests",UUID.class).toString();
            var decisionGate=new CountDownLatch(1);
            var c=pool.submit(()->{decisionGate.await();return decidePermission(id,"APPROVED",null,hr()).andReturn().getResponse().getStatus();});
            var d=pool.submit(()->{decisionGate.await();return decidePermission(id,"REJECTED","No",hr()).andReturn().getResponse().getStatus();});decisionGate.countDown();
            assertThat(List.of(c.get(10,TimeUnit.SECONDS),d.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,412);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_request_history",Integer.class)).isEqualTo(2);
    }
    @Test
    void auditFailureAndDownstreamFailureRollBackRequestAndIdempotency() throws Exception {
        String shift=assignedRequestShift(),body=permissionBody(shift,"LATE_ARRIVAL","13:08");
        source.fail=true;submitPermission(body,"failed-source",employee()).andExpect(status().isServiceUnavailable());source.fail=false;
        jdbc.execute("ALTER TABLE attendance_request_history ADD CONSTRAINT test_request_audit CHECK (action <> 'SUBMITTED')");
        try {submitPermission(body,"failed-audit",employee()).andExpect(status().isConflict());}
        finally {jdbc.execute("ALTER TABLE attendance_request_history DROP CONSTRAINT test_request_audit");}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_requests",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_operations WHERE operation_type='CREATE_ATTENDANCE_REQUEST'",Integer.class)).isZero();
        submitPermission(body,"failed-audit",employee()).andExpect(status().isCreated());
    }
    private String assignedRequestShift() throws Exception {
        String id=createShift("13:00","15:00");apply(schedule(id,"COMPANY_DEFAULT",List.of(),DATE,null),"request-schedule",0);return id;
    }
    private String permissionBody(String shift,String type,String time) {
        return """
            {"workDate":"%s","shiftId":"%s","shiftVersion":0,"requestType":"%s","period":"AFTERNOON","expectedTime":"%s","reason":"Private request reason","requestedMinutes":999,"employeeId":"%s","status":"APPROVED"}
            """.formatted(DATE,shift,type,time,OTHER);
    }
    private ResultActions submitPermission(String body,String key,RequestPostProcessor actor) throws Exception {
        return mvc.perform(post(BASE+"/requests").with(actor).header("Idempotency-Key",key).contentType("application/json").content(body));
    }
    @Test
    void overtimeNeedsApprovalAndCountsOnlyActualApprovedMinutesWithSeparateReportColumn() throws Exception {
        String shift=createShift("13:00","17:00"); apply(schedule(shift,"COMPANY_DEFAULT",List.of(),DATE,null),"default",0);
        String id=permissionId(createOt("18:00","20:00","ot"));
        mvc.perform(post(BASE+"/overtime/"+id+"/check-in").with(employee()).header("Idempotency-Key","pending")).andExpect(status().isConflict());
        decideExtra("overtime",id,"APPROVED",actor(EMPLOYEE,"HR"),0).andExpect(status().isForbidden());
        decideExtra("overtime",id,"APPROVED",hr(),0).andExpect(status().isOk()).andExpect(jsonPath("$.approvedMinutes").value(120));
        clock.at("18:10");
        mvc.perform(post(BASE+"/overtime/"+id+"/check-in").with(other()).header("Idempotency-Key","other")).andExpect(status().isNotFound());
        mvc.perform(post(BASE+"/overtime/"+id+"/check-in").with(employee()).with(r->{r.setRemoteAddr("203.0.113.8");return r;}).header("Idempotency-Key","network")).andExpect(status().isForbidden());
        mvc.perform(post(BASE+"/overtime/"+id+"/check-in").with(employee()).header("Idempotency-Key","ot-in")).andExpect(status().isOk()).andExpect(jsonPath("$.countedMinutes").doesNotExist());
        clock.at("20:30");
        String out=mvc.perform(post(BASE+"/overtime/"+id+"/check-out").with(employee()).header("Idempotency-Key","ot-out"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.countedMinutes").value(110)).andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(post(BASE+"/overtime/"+id+"/check-out").with(employee()).header("Idempotency-Key","ot-out")).andReturn().getResponse().getContentAsString()).isEqualTo(out);
        mvc.perform(get(BASE+"/mine?from="+DATE+"&until="+DATE).with(employee())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].overtime.countedMinutes").value(110)).andExpect(jsonPath("$[0].workMinutesCounted").doesNotExist());
        String csv=mvc.perform(get(BASE+"/reports/export.csv?employeeId="+EMPLOYEE+"&from="+DATE+"&until="+DATE).with(hr())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("approved_ot_minutes,counted_ot_minutes").contains("\"120\",\"110\"");
    }
    @Test
    void overtimeRejectsRegularHoursDuplicatesStaleDecisionsAndScheduleChanges() throws Exception {
        String shift=createShift("13:00","17:00"); apply(schedule(shift,"COMPANY_DEFAULT",List.of(),DATE,null),"default",0);
        createOt("16:00","18:00","regular").andExpect(status().isConflict());
        String id=permissionId(createOt("18:00","20:00","ot"));
        createOt("19:00","21:00","overlap").andExpect(status().isConflict());
        decideExtra("overtime",id,"APPROVED",hr(),0).andExpect(status().isOk());
        decideExtra("overtime",id,"REJECTED",hr(),0).andExpect(status().isPreconditionFailed());
        mvc.perform(post(BASE+"/schedules/apply").with(hr()).header("If-Match","\"1\"").header("Idempotency-Key","change").contentType("application/json").content(schedule(shift,"ALL_EMPLOYEES",List.of(),DATE,null))).andExpect(status().isConflict());
        mvc.perform(get(BASE+"/overtime/inbox").with(employee())).andExpect(status().isForbidden());
    }
    @Test
    void pendingCancelledRejectedAndExpiredOvertimeNeverCount() throws Exception {
        String id=permissionId(createOt("18:00","20:00","first"));
        decideExtra("overtime",id,"REJECTED",hr(),0).andExpect(status().isBadRequest());
        mvc.perform(post(BASE+"/overtime/"+id+"/cancel").with(employee()).header("If-Match","\"0\"")).andExpect(status().isOk());
        String second=permissionId(createOt("18:00","20:00","second"));
        clock.at("18:00");
        decideExtra("overtime",second,"APPROVED",hr(),0).andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/mine?from="+DATE+"&until="+DATE).with(employee())).andExpect(status().isOk()).andExpect(jsonPath("$[0].overtime.countedMinutes").value(0));
    }
    @Test
    void overnightShiftChecksOutNextDayAndKeepsItsStartingWorkDate() throws Exception {
        String body="""
            {"name":"Night","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh","overnight":true,
             "intervals":[{"period":"AFTERNOON","start":"22:00","end":"06:00"}],"checkInFrom":"21:00","checkOutUntil":"09:00"}
            """;
        String id=permissionId(mvc.perform(post(BASE+"/shifts").with(hr()).contentType("application/json").content(body)).andExpect(jsonPath("$.requiredMinutes").value(480)));
        apply(schedule(id,"COMPANY_DEFAULT",List.of(),DATE,null),"night",0);
        String morning=createShift("08:00","12:00");
        apply(schedule(morning,"SELECTED_EMPLOYEES",List.of(EMPLOYEE),DATE.plusDays(1),DATE.plusDays(1)).replace("MONDAY","TUESDAY"),"next-morning",1);
        clock.at("22:00"); punch("check-in","night-in").andExpect(status().isOk());
        clock.time=DATE.plusDays(1).atTime(6,0).atZone(clock.getZone()).toInstant();
        punch("check-out","night-out").andExpect(status().isOk()).andExpect(jsonPath("$.workDate").value(DATE.toString())).andExpect(jsonPath("$.workMinutesCounted").value(480));
        clock.time=DATE.plusDays(1).atTime(8,0).atZone(clock.getZone()).toInstant();
        punch("check-in","morning-in").andExpect(status().isOk()).andExpect(jsonPath("$.workDate").value(DATE.plusDays(1).toString()));
    }
    @Test
    void overtimeOnRestDayCanCrossMidnightWithoutRegularAttendance() throws Exception {
        String body=mapper.writeValueAsString(Map.of("start",DATE+"T23:00","end",DATE.plusDays(1)+"T01:00","reason","Night maintenance"));
        String id=permissionId(mvc.perform(post(BASE+"/overtime").with(employee()).header("Idempotency-Key","night-ot").contentType("application/json").content(body)));
        decideExtra("overtime",id,"APPROVED",hr(),0).andExpect(status().isOk());
        clock.at("23:10"); mvc.perform(post(BASE+"/overtime/"+id+"/check-in").with(employee()).header("Idempotency-Key","in")).andExpect(status().isOk());
        clock.time=DATE.plusDays(1).atTime(1,30).atZone(clock.getZone()).toInstant();
        mvc.perform(post(BASE+"/overtime/"+id+"/check-out").with(employee()).header("Idempotency-Key","out")).andExpect(status().isOk()).andExpect(jsonPath("$.countedMinutes").value(110));
        mvc.perform(get(BASE+"/mine?from="+DATE+"&until="+DATE).with(employee())).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("NO_SCHEDULE")).andExpect(jsonPath("$[0].overtime.countedMinutes").value(110));
    }
    @Test
    void overnightAssignmentsCannotOverlapFollowingDefaultShift() throws Exception {
        String morning=createShift("08:00","12:00");
        apply(schedule(morning,"COMPANY_DEFAULT",List.of(),DATE,null).replace("[\"MONDAY\"]","[\"MONDAY\",\"TUESDAY\"]"),"default",0);
        String body="""
            {"name":"Night","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh","overnight":true,
             "intervals":[{"period":"AFTERNOON","start":"22:00","end":"10:00"}],"checkInFrom":"21:00","checkOutUntil":"11:00"}
            """;
        String night=permissionId(mvc.perform(post(BASE+"/shifts").with(hr()).contentType("application/json").content(body)));
        mvc.perform(post(BASE+"/schedules/apply").with(hr()).header("If-Match","\"1\"").header("Idempotency-Key","overlap").contentType("application/json").content(schedule(night,"SELECTED_EMPLOYEES",List.of(EMPLOYEE),DATE,DATE))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_schedule_rules WHERE employee_id IS NOT NULL",Integer.class)).isZero();
    }
    @Test
    void nextDayApprovedOvertimeIsVisibleInOvernightSchedulePreview() throws Exception {
        String otBody=mapper.writeValueAsString(Map.of("start",DATE.plusDays(1)+"T06:00","end",DATE.plusDays(1)+"T08:00","reason","Maintenance"));
        String ot=permissionId(mvc.perform(post(BASE+"/overtime").with(employee()).header("Idempotency-Key","next-day").contentType("application/json").content(otBody)));
        decideExtra("overtime",ot,"APPROVED",hr(),0).andExpect(status().isOk());
        String body="""
            {"name":"Night","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh","overnight":true,
             "intervals":[{"period":"AFTERNOON","start":"22:00","end":"08:00"}],"checkInFrom":"21:00","checkOutUntil":"09:00"}
            """;
        String night=permissionId(mvc.perform(post(BASE+"/shifts").with(hr()).contentType("application/json").content(body)));
        mvc.perform(post(BASE+"/schedules/preview").with(hr()).contentType("application/json").content(schedule(night,"COMPANY_DEFAULT",List.of(),DATE,DATE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvedOvertimeConflicts").value(1));
    }
    private ResultActions createOt(String start,String end,String key) throws Exception {
        return mvc.perform(post(BASE+"/overtime").with(employee()).header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(Map.of("start",DATE+"T"+start,"end",DATE+"T"+end,"reason","Maintenance"))));
    }
    private ResultActions decideExtra(String resource,String id,String decision,RequestPostProcessor actor,long version) throws Exception {
        return mvc.perform(post(BASE+"/"+resource+"/"+id+"/decision").with(actor).header("If-Match","\""+version+"\"").contentType("application/json").content(mapper.writeValueAsString(Map.of("status",decision,"reviewNote",""))));
    }
    private void seedDefaultTemplate() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.FileSystemResource(TEST_SCHEMA))
                .execute(jdbc.getDataSource());
    }
    @Test
    void initialDefaultRepeatsOnWeekdaysForAllEmployeesAndCanBePunched() throws Exception {
        seedDefaultTemplate();
        defaultSchedule.initialize();
        defaultSchedule.initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_schedule_rules",Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_schedule_batches",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revision FROM attendance_schedule_state WHERE id=1",Long.class)).isEqualTo(1);
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE+"&until="+DATE.plusDays(7)).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].source").value("COMPANY_DEFAULT"))
                .andExpect(jsonPath("$[0].requiredMinutes").value(480))
                .andExpect(jsonPath("$[4].requiredMinutes").value(480))
                .andExpect(jsonPath("$[5].source").value("NO_SCHEDULE"))
                .andExpect(jsonPath("$[6].source").value("NO_SCHEDULE"))
                .andExpect(jsonPath("$[7].requiredMinutes").value(480));
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE+"&until="+DATE).with(actor(UUID.randomUUID(),"EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].source").value("COMPANY_DEFAULT"));
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE.minusDays(3)+"&until="+DATE.minusDays(3)).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].source").value("NO_SCHEDULE"));
        clock.at("08:00"); punch("check-in","default-in").andExpect(status().isOk());
        clock.at("17:30"); punch("check-out","default-out").andExpect(status().isOk()).andExpect(jsonPath("$.workMinutesCounted").value(480));
    }
    @Test
    void initialDefaultPreservesPersonalOverridesAndNeverRewritesHrCompanySchedules() throws Exception {
        seedDefaultTemplate();
        String personal=createShift("13:00","15:00");
        apply(schedule(personal,"SELECTED_EMPLOYEES",List.of(EMPLOYEE),DATE,DATE),"personal",0);
        defaultSchedule.initialize();
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE+"&until="+DATE.plusDays(1)).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requiredMinutes").value(120))
                .andExpect(jsonPath("$[0].source").value("EMPLOYEE_OVERRIDE"))
                .andExpect(jsonPath("$[1].requiredMinutes").value(480));
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE+"&until="+DATE).with(other()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requiredMinutes").value(480));
        apply(schedule(personal,"COMPANY_DEFAULT",List.of(),DATE,null),"hr-default",2);
        defaultSchedule.initialize();
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE.plusDays(7)+"&until="+DATE.plusDays(7)).with(other()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requiredMinutes").value(120));
        assertThat(jdbc.queryForObject("SELECT revision FROM attendance_schedule_state WHERE id=1",Long.class)).isEqualTo(3);
    }
    @Test
    void initializerRespectsExplicitWeekdaysAndInactiveDefaultTemplate() throws Exception {
        seedDefaultTemplate();
        jdbc.update("UPDATE work_shifts SET active=false WHERE id=?",com.company.attendance.service.DefaultScheduleInitializer.DEFAULT_SHIFT_ID);
        defaultSchedule.initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_schedule_rules",Integer.class)).isZero();
        jdbc.update("UPDATE work_shifts SET active=true WHERE id=?",com.company.attendance.service.DefaultScheduleInitializer.DEFAULT_SHIFT_ID);
        String custom=createShift("13:00","15:00");
        apply(schedule(custom,"COMPANY_DEFAULT",List.of(),DATE,null).replace("MONDAY","TUESDAY"),"custom",0);
        defaultSchedule.initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_schedule_rules",Integer.class)).isEqualTo(1);
        mvc.perform(get(BASE+"/schedules/mine?from="+DATE+"&until="+DATE).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].source").value("NO_SCHEDULE"));
    }
    @Test
    void monthlyReportUsesBoundedQueriesAndKeepsSnapshotsAndCoverageIsolated() throws Exception {
        String company = createShift("13:00", "17:00");
        String personal = createShift("13:00", "15:00");
        apply(schedule(company, "COMPANY_DEFAULT", List.of(), DATE, null), "company", 0);
        apply(schedule(personal, "SELECTED_EMPLOYEES", List.of(EMPLOYEE), DATE, DATE), "personal", 1);
        String request = permissionId(submitPermission(permissionBody(personal, "LATE_ARRIVAL", "13:08"), "late", employee()));
        decidePermission(request, "APPROVED", null, hr()).andExpect(status().isOk());
        String ot = permissionId(createOt("18:00", "20:00", "ot"));
        decideExtra("overtime", ot, "APPROVED", hr(), 0).andExpect(status().isOk());
        clock.at("13:08");
        punch("check-in", "in").andExpect(status().isOk());
        clock.at("15:00");
        punch("check-out", "out").andExpect(status().isOk());
        // Later source changes must not replace the saved day's leave snapshot.
        source.leaves = List.of(new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE, DATE, "FULL_DAY", "APPROVED", 1));
        LocalDate until = DATE.plusDays(30);
        clock.time = until.atTime(21, 0).atZone(clock.getZone()).toInstant();
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get(BASE + "/mine?from=" + DATE + "&until=" + DATE).with(employee()))
                    .andExpect(status().isOk());
            long oneDayQueries = statistics.getPrepareStatementCount();
            statistics.clear();
            mvc.perform(get(BASE + "/mine?from=" + DATE + "&until=" + until).with(employee()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(31))
                    .andExpect(jsonPath("$[0].shiftId").value(personal))
                    .andExpect(jsonPath("$[0].workMinutesCounted").value(105))
                    .andExpect(jsonPath("$[0].annualLeaveMinutes").value(0))
                    .andExpect(jsonPath("$[0].permissionCoverage.approvedRequestIds[0]").value(request))
                    .andExpect(jsonPath("$[0].overtime.approvedMinutes").value(120))
                    .andExpect(jsonPath("$[1].status").value("NO_SCHEDULE"))
                    .andExpect(jsonPath("$[7].shiftId").value(company))
                    .andExpect(jsonPath("$[7].permissionCoverage.approvedRequestIds").isEmpty())
                    .andExpect(jsonPath("$[7].overtime.approvedMinutes").value(0));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(oneDayQueries).isEqualTo(4);
            mvc.perform(get(BASE + "/reports?employeeId=" + OTHER + "&from=" + DATE + "&until=" + until).with(hr()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].shiftId").value(company))
                    .andExpect(jsonPath("$[0].permissionCoverage.approvedRequestIds").isEmpty())
                    .andExpect(jsonPath("$[0].overtime.approvedMinutes").value(0));
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void monthlyScheduleUsesTwoQueriesAndResolvesExpiredOverrides() throws Exception {
        String company = createShift("13:00", "17:00");
        String personal = createShift("13:00", "15:00");
        apply(schedule(company, "COMPANY_DEFAULT", List.of(), DATE, null), "company", 0);
        apply(schedule(personal, "SELECTED_EMPLOYEES", List.of(EMPLOYEE), DATE, DATE), "personal", 1);
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get(BASE + "/schedules/mine?from=" + DATE + "&until=" + DATE.plusDays(30)).with(employee()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(31))
                    .andExpect(jsonPath("$[0].source").value("EMPLOYEE_OVERRIDE"))
                    .andExpect(jsonPath("$[0].requiredMinutes").value(120))
                    .andExpect(jsonPath("$[1].source").value("NO_SCHEDULE"))
                    .andExpect(jsonPath("$[7].source").value("COMPANY_DEFAULT"))
                    .andExpect(jsonPath("$[7].requiredMinutes").value(240));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void inheritedAuditMetadataPersistsAndPreservesHistoryJsonFields() throws Exception {
        String shift = assignedRequestShift();
        String request = permissionId(submitPermission(permissionBody(shift, "LATE_ARRIVAL", "13:08"), "late", employee()));
        mvc.perform(get(BASE + "/shifts/" + shift + "/history").with(hr()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].actorUserId").value(OTHER.toString()))
                .andExpect(jsonPath("$.content[0].occurredAt").value(clock.instant().toString()));
        mvc.perform(get(BASE + "/schedules/history").with(hr()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].actorUserId").value(OTHER.toString()))
                .andExpect(jsonPath("$.content[0].occurredAt").value(clock.instant().toString()));
        mvc.perform(get(BASE + "/requests/" + request + "/history").with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].actorUserId").value(EMPLOYEE.toString()))
                .andExpect(jsonPath("$.content[0].occurredAt").value(clock.instant().toString()));
        clock.at("13:00");
        String time = clock.instant().toString();
        punch("check-in", "in").andExpect(status().isOk());
        mvc.perform(get(BASE + "/employees/" + EMPLOYEE + "/days/" + DATE + "/events").with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].actorUserId").value(EMPLOYEE.toString()))
                .andExpect(jsonPath("$[0].eventAt").value(time))
                .andExpect(jsonPath("$[0].occurredAt").doesNotExist());
    }

    @Test
    void normalizedRevisionKeepsHistoricalWorkAndDatabaseRejectsMissingRevisionAndDuplicateEvent() throws Exception {
        String shift = assignedRequestShift();
        clock.at("13:00");
        punch("check-in", "in").andExpect(status().isOk());
        mvc.perform(put(BASE + "/shifts/" + shift).with(hr()).header("If-Match", "\"0\"")
                .contentType("application/json").content(shift("13:00", "17:00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        clock.at("15:00");
        punch("check-out", "out").andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftVersion").value(0))
                .andExpect(jsonPath("$.workMinutesCounted").value(120));
        mvc.perform(get(BASE + "/schedules/mine?from=" + DATE + "&until=" + DATE.plusDays(7)).with(employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[7].requiredMinutes").value(120));
        assertThatThrownBy(() -> jdbc.update("UPDATE attendance_daily SET shift_version=999 WHERE employee_id=?", EMPLOYEE))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance_events(id,day_id,event_type,event_at,method,actor_user_id,source_ip) SELECT ?,day_id,event_type,event_at,method,actor_user_id,source_ip FROM attendance_events WHERE event_type='CHECK_IN'", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_schema)=lower(current_schema()) AND ((lower(table_name) IN ('attendance_daily','attendance_requests') AND lower(column_name)='shift_definition') OR (lower(table_name)='work_schedule_rules' AND lower(column_name)='definition'))", Integer.class)).isZero();
    }

    @Test
    void compactSnapshotsKeepOnlyReportIdentityAndCoverageForThatDay() throws Exception {
        assignedRequestShift();
        var pendingId = UUID.randomUUID();
        source.leaves = List.of(
                new WorkforceClient.Leave(pendingId, EMPLOYEE, "ANNUAL", DATE, DATE, "FULL_DAY", "PENDING", 0),
                new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE.plusDays(1), DATE.plusDays(1), "FULL_DAY", "APPROVED", 1),
                new WorkforceClient.Leave(UUID.randomUUID(), EMPLOYEE, "ANNUAL", DATE, DATE, "FULL_DAY", "CANCELLED", 1));
        source.holidays = List.of(new WorkforceClient.Holiday(1L, "HOLIDAY", "PUBLISHED", "ALL", true, DATE.plusDays(1), DATE.plusDays(1)));
        clock.at("13:00");
        punch("check-in", "in").andExpect(status().isOk());
        var stored = jdbc.queryForMap("SELECT employee_snapshot,leave_snapshot,holiday_snapshot FROM attendance_daily WHERE employee_id=?", EMPLOYEE);
        var person = mapper.readTree(stored.get("employee_snapshot").toString());
        assertThat(person.has("status")).isFalse();
        assertThat(person.has("hireDate")).isFalse();
        assertThat(person.get("employeeCode").asString()).isEqualTo("=EMP001");
        var leaves = mapper.readTree(stored.get("leave_snapshot").toString());
        assertThat(leaves.size()).isEqualTo(1);
        assertThat(leaves.get(0).get("id").asString()).isEqualTo(pendingId.toString());
        assertThat(mapper.readTree(stored.get("holiday_snapshot").toString()).isEmpty()).isTrue();
    }

    @Test
    void expiringPunchResponsesKeepsKeysAuditAndPreventsReplayFromCreatingNewWork() throws Exception {
        assignedRequestShift();
        clock.at("13:00");
        punch("check-in", "old-in").andExpect(status().isOk());
        clock.at("15:00");
        punch("check-out", "old-out").andExpect(status().isOk());
        clock.time = clock.instant().plus(Duration.ofDays(181));
        operationService.expirePunchResponses();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_operations WHERE operation_type IN ('CHECK_IN','CHECK_OUT') AND response_body IS NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_operations WHERE operation_type='APPLY_SCHEDULE' AND response_body IS NOT NULL", Integer.class)).isEqualTo(1);
        punch("check-in", "old-in").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_daily", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_events", Integer.class)).isEqualTo(2);
    }

    private String permissionId(ResultActions action) throws Exception {
        return mapper.readTree(action.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }
    private ResultActions decidePermission(String id,String status,String note,RequestPostProcessor actor) throws Exception {
        Map<String,Object> body=new HashMap<>();body.put("status",status);body.put("reviewNote",note);
        return mvc.perform(post(BASE+"/requests/"+id+"/decision").with(actor).header("If-Match","\"0\"").contentType("application/json").content(mapper.writeValueAsString(body)));
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
