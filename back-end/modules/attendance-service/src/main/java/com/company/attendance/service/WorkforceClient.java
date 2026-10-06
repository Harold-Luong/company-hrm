package com.company.attendance.service;

import com.company.attendance.config.AttendanceProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

/** Read only calls with the current caller's Bearer token; no cross-database access. */
@Component
@EnableConfigurationProperties(AttendanceProperties.class)
public class WorkforceClient {
    private final AttendanceProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public WorkforceClient(AttendanceProperties properties, ObjectMapper mapper) {
        this.properties = properties; this.mapper = mapper;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Employee(UUID id, String employeeCode, String firstName, String lastName,
                           String status, LocalDate hireDate, Reference department) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reference(UUID id, String code, String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmployeePage(List<Employee> content, int totalPages) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leave(UUID id, UUID employeeId, String leaveType, LocalDate startDate, LocalDate endDate,
                        String period, String status, long version) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Holiday(Long id, String type, String status, String audienceType,
                          boolean allDay, LocalDate startDate, LocalDate endDate) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Calendar(List<Holiday> events) {}

    public Employee employee(UUID id, JwtAuthenticationToken actor) {
        Employee result = read(properties.employeeUrl(), "/api/v1/employees/" + id, actor, Employee.class);
        if (!id.equals(result.id()) || result.hireDate() == null || result.status() == null)
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Employee returned incomplete data");
        return result;
    }
    public boolean eligible(Employee employee, LocalDate date) {
        return Set.of("ACTIVE", "PROBATION").contains(employee.status()) && !employee.hireDate().isAfter(date);
    }
    public List<Employee> employees(JwtAuthenticationToken actor) {
        requireReviewer(actor);
        List<Employee> result = new ArrayList<>();
        for (int page = 0; page < 100; page++) {
            EmployeePage data = read(properties.employeeUrl(), "/api/v1/employees?page=" + page + "&size=100", actor, EmployeePage.class);
            if (data.content() == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Employee returned incomplete data");
            result.addAll(data.content());
            if (page + 1 >= data.totalPages()) return result;
        }
        throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Select a smaller employee scope");
    }
    public List<Leave> leaves(UUID employee, LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
        String path = "/api/v1/leave/requests/attendance?from=" + from + "&until=" + until
                + (employee == null ? "" : "&employeeId=" + employee);
        return List.of(read(properties.leaveUrl(), path, actor, Leave[].class));
    }
    public List<Holiday> holidays(LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
        List<Holiday> result = new ArrayList<>();
        for (int year = from.getYear(); year <= until.getYear(); year++) {
            var data = read(properties.calendarUrl(), "/api/v1/calendar?year=" + year, actor, Calendar.class);
            if (data.events() == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Calendar returned incomplete data");
            data.events().stream().filter(e -> e.allDay() && "HOLIDAY".equals(e.type()) && "PUBLISHED".equals(e.status())
                    && "ALL".equals(e.audienceType()) && e.startDate() != null && e.endDate() != null
                    && !e.startDate().isAfter(until) && !e.endDate().isBefore(from)).forEach(result::add);
        }
        return result.stream().distinct().toList();
    }
    private <T> T read(String base, String path, JwtAuthenticationToken actor, Class<T> type) {
        try {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + actor.getToken().getTokenValue()).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw error(HttpStatus.valueOf(response.statusCode()), "Source service denied access");
            if (response.statusCode() == 404) throw error(HttpStatus.NOT_FOUND, "Source resource not found");
            if (response.statusCode() != 200) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Source service is unavailable");
            T result = mapper.readValue(response.body(), type);
            if (result == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Source returned no data");
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Source request interrupted");
        } catch (java.io.IOException | tools.jackson.core.JacksonException exception) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Source service is unavailable or returned invalid data");
        }
    }
}
