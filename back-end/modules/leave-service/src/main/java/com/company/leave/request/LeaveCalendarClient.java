package com.company.leave.request;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static com.company.leave.request.LeaveModels.*;

@Component
public class LeaveCalendarClient {
    public record Day(LocalDate date, UUID shiftId, Long shiftVersion, int fullUnits,
                      int morningUnits, int afternoonUnits) {
        public int units(LeavePeriod period) {
            return switch (period) { case FULL_DAY -> fullUnits; case MORNING -> morningUnits; case AFTERNOON -> afternoonUnits; };
        }
    }
    private final String base;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public LeaveCalendarClient(@Value("${attendance.service-url:http://localhost:8085}") String base, ObjectMapper mapper) {
        this.base = base; this.mapper = mapper;
    }
    public List<Day> calendar(UUID employee, LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
        try {
            var request = HttpRequest.newBuilder(URI.create(base + "/api/v1/attendance/leave-calendar?employeeId="
                + employee + "&from=" + from + "&until=" + until)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + actor.getToken().getTokenValue()).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), "Schedule access denied");
            if (response.statusCode() != 200) throw unavailable();
            var parsed = mapper.readValue(response.body(), Day[].class);
            if (parsed == null || Arrays.stream(parsed).anyMatch(Objects::isNull)) throw unavailable();
            var days = List.of(parsed);
            var expected = from.datesUntil(until.plusDays(1)).toList();
            if (days.size() != expected.size()) throw unavailable();
            for (int i = 0; i < days.size(); i++) {
                var d = days.get(i);
                if (!expected.get(i).equals(d.date()) || (d.fullUnits() != 0 && d.fullUnits() != 2)
                        || d.morningUnits() < 0 || d.morningUnits() > 1 || d.afternoonUnits() < 0 || d.afternoonUnits() > 1
                        || (d.fullUnits() == 0 && d.morningUnits() + d.afternoonUnits() != 0)) throw unavailable();
            }
            return days;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (java.io.IOException | tools.jackson.core.JacksonException | IllegalArgumentException e) { throw unavailable(); }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Working calendar is unavailable or incomplete");
    }
}
