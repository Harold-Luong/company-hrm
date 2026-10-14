package com.company.leave;

import com.company.leave.request.LeaveCalendarClient;
import com.company.leave.request.LeaveModels.LeavePeriod;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LeaveCalendarClientTests {
    HttpServer server;
    LeaveCalendarClient client;
    String body;
    int status;
    String authorization;
    final UUID employee = UUID.randomUUID();
    final LocalDate date = LocalDate.of(2030, 2, 1);
    final JwtAuthenticationToken actor = new JwtAuthenticationToken(Jwt.withTokenValue("calendar-access")
        .header("alg", "RS256").subject("employee").build());
    @BeforeEach void start() throws Exception {
        status = 200;
        body = "[{\"date\":\"2030-02-01\",\"shiftId\":null,\"shiftVersion\":null,\"fullUnits\":2,\"morningUnits\":1,\"afternoonUnits\":1}]";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/attendance/leave-calendar", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        });
        server.start();
        client = new LeaveCalendarClient("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }
    @Test void relaysCallerAndReadsUnits() {
        var days = client.calendar(employee, date, date, actor);
        assertThat(authorization).isEqualTo("Bearer calendar-access");
        assertThat(days).singleElement().satisfies(d -> {
            assertThat(d.units(LeavePeriod.FULL_DAY)).isEqualTo(2);
            assertThat(d.units(LeavePeriod.MORNING)).isEqualTo(1);
        });
    }
    @Test void partialDuplicateAndMalformedCalendarsNeverBecomeFreeLeave() {
        for (String invalid : new String[]{"[]", "null", "[null]", "{}", "invalid", body.replace("2030-02-01", "2030-02-02"), body.replace("\"fullUnits\":2", "\"fullUnits\":-1")}) {
            body = invalid;
            assertThatThrownBy(() -> client.calendar(employee, date, date, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        }
    }
    @Test void preservesAccessDenialsAndFailsClosedOnServerErrors() {
        for (int code : new int[]{401, 403, 404, 500, 302}) {
            status = code;
            assertThatThrownBy(() -> client.calendar(employee, date, date, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(code == 401 || code == 403 ? code : 503));
        }
    }
}
