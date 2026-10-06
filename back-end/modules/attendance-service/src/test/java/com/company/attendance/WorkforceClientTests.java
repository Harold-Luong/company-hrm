package com.company.attendance;

import com.company.attendance.config.AttendanceProperties;
import com.company.attendance.service.WorkforceClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkforceClientTests {
    HttpServer server;
    WorkforceClient client;
    String body, authorization, requestedPath;
    int status = 200;
    final UUID employee = UUID.fromString("10000000-0000-0000-0000-000000000001");
    final LocalDate date = LocalDate.of(2030, 1, 7);
    final JwtAuthenticationToken actor = new JwtAuthenticationToken(Jwt.withTokenValue("access-token")
            .header("alg", "RS256").subject("user").claim("employee_id", employee.toString()).build());

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requestedPath = exchange.getRequestURI().toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new WorkforceClient(new AttendanceProperties(url, url, url, List.of(), List.of()), new ObjectMapper());
    }
    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void relaysBearerAndReadsEmployeeAndLeaveContractsIgnoringPrivateFields() {
        body = """
                {"id":"%s","employeeCode":"EMP1","firstName":"An","lastName":"Nguyen",
                "status":"ACTIVE","hireDate":"2029-01-01","department":null,"email":"private@example.com"}
                """.formatted(employee);
        assertThat(client.employee(employee, actor).hireDate()).isEqualTo(LocalDate.of(2029, 1, 1));
        assertThat(authorization).isEqualTo("Bearer access-token");
        assertThat(requestedPath).isEqualTo("/api/v1/employees/" + employee);
        body = """
                [{"id":"%s","employeeId":"%s","leaveType":"ANNUAL","startDate":"2030-01-07",
                "endDate":"2030-01-07","period":"MORNING","status":"APPROVED","version":2}]
                """.formatted(UUID.randomUUID(), employee);
        assertThat(client.leaves(employee, date, date, actor)).singleElement().satisfies(leave -> {
            assertThat(leave.period()).isEqualTo("MORNING"); assertThat(leave.version()).isEqualTo(2);
        });
        assertThat(requestedPath).isEqualTo("/api/v1/leave/requests/attendance?from=2030-01-07&until=2030-01-07&employeeId=" + employee);
    }
    @Test
    void onlyPublishedAllDayCompanyHolidaysReduceRequiredWork() {
        body = """
                {"year":2030,"availableYears":[2030],"events":[
                {"id":1,"type":"HOLIDAY","status":"PUBLISHED","audienceType":"ALL","allDay":true,"startDate":"2030-01-07","endDate":"2030-01-07"},
                {"id":2,"type":"HOLIDAY","status":"DRAFT","audienceType":"ALL","allDay":true,"startDate":"2030-01-07","endDate":"2030-01-07"},
                {"id":3,"type":"EVENT","status":"PUBLISHED","audienceType":"ALL","allDay":true,"startDate":"2030-01-07","endDate":"2030-01-07"}]}
                """;
        assertThat(client.holidays(date, date, actor)).extracting(WorkforceClient.Holiday::id).containsExactly(1L);
        assertThat(requestedPath).isEqualTo("/api/v1/calendar?year=2030");
    }
    @Test
    void sourceErrorsAndInvalidJsonNeverBecomeEmptyCoverage() {
        body = "[]";
        for (int response : List.of(401, 403, 404, 500)) {
            status = response;
            assertThatThrownBy(() -> client.leaves(employee, date, date, actor))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(response == 500 ? 503 : response));
        }
        status = 200; body = "invalid json";
        assertThatThrownBy(() -> client.leaves(employee, date, date, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
    }
}
