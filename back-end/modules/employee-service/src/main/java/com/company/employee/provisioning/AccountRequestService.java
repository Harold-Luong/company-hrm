package com.company.employee.provisioning;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRequestService {
    private final JdbcTemplate jdbc;
    private final EventOutbox outbox;
    private final ObjectMapper mapper;
    private final Validator validator;
    @Value("${hrm.events.enabled:false}")
    private boolean enabled;
    @Value("${hrm.events.request-topic:hrm.employee.account-requests.v1}")
    private String topic;

    public record Request(@NotBlank @Email @Size(max = 255) String email) {
        public Request {
            email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record Response(UUID requestId, UUID employeeId, String provisioningStatus, String errorCode,
            String accountStatus, OffsetDateTime createdAt) {
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public Response request(UUID employeeId, UUID idempotencyKey, Request body) {
        if (!enabled)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account provisioning is disabled");
        if (employeeId == null || idempotencyKey == null || body == null || !validator.validate(body).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valid employeeId, Idempotency-Key and email are required");
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        String email = body.email().strip().toLowerCase(Locale.ROOT);
        // All requests and results for one employee serialize on the source-of-truth
        // row.
        if (jdbc.queryForList("SELECT id FROM employees WHERE id = ? FOR UPDATE", UUID.class, employeeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        var existing = jdbc.queryForList("""
                SELECT request_id FROM account_provisioning_requests WHERE requested_by = ? AND idempotency_key = ?
                """, UUID.class, actor, idempotencyKey);
        if (!existing.isEmpty()) {
            var stored = jdbc.queryForMap(
                    "SELECT employee_id, email FROM account_provisioning_requests WHERE request_id = ?",
                    existing.getFirst());
            if (!employeeId.equals(stored.get("employee_id")) || !email.equals(stored.get("email"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used with different data");
            }
            return read(employeeId, existing.getFirst());
        }
        if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_provisioning_requests WHERE employee_id = ? AND status = 'PENDING'",
                Integer.class, employeeId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account request is already pending");
        }
        UUID requestId = UUID.randomUUID();
        var event = EmployeeAccountRequested.create(requestId, employeeId, email, actor);
        jdbc.update(
                """
                        INSERT INTO account_provisioning_requests
                        (request_id, employee_id, pending_employee_id, email, requested_by, idempotency_key, correlation_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                        """,
                requestId, employeeId, employeeId, email, actor, idempotencyKey, event.correlationId());
        outbox.append(event.eventId(), topic, employeeId, mapper.writeValueAsString(event));
        return read(employeeId, requestId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public Response find(UUID employeeId, UUID requestId) {
        if (!enabled)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account provisioning is disabled");
        return read(employeeId, requestId);
    }

    private Response read(UUID employeeId, UUID requestId) {
        var results = jdbc.query("""
                SELECT r.request_id, r.employee_id, r.status, r.error_code, e.account_status, r.created_at
                FROM account_provisioning_requests r JOIN employees e ON e.id = r.employee_id
                WHERE r.request_id = ? AND r.employee_id = ?
                """, (rs, row) -> new Response(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, OffsetDateTime.class)), requestId,
                employeeId);
        if (results.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account request not found");
        return results.getFirst();
    }

    @Transactional
    public void apply(AccountProvisioningResult event) {
        if (!validator.validate(event).isEmpty() || event.schemaVersion() != 1
                || !"auth-service".equals(event.producer())) {
            throw new IllegalArgumentException("Invalid account result envelope");
        }
        boolean success = "AccountCreated".equals(event.eventType());
        if (success) {
            if (!java.util.Set.of("PENDING_ACTIVATION", "ACTIVE", "DISABLED")
                    .contains(Objects.toString(event.data().accountStatus(), ""))
                    || event.data().accountVersion() == null || event.data().accountVersion() < 1
                    || event.data().errorCode() != null) {
                throw new IllegalArgumentException("Invalid successful account result");
            }
        } else if (!"AccountCreationFailed".equals(event.eventType())
                || !java.util.Set.of("ACCOUNT_ALREADY_EXISTS", "EMAIL_ALREADY_USED")
                        .contains(Objects.toString(event.data().errorCode(), ""))
                || event.data().accountStatus() != null || event.data().accountVersion() != null) {
            throw new IllegalArgumentException("Invalid failed account result");
        }
        UUID employeeId = event.data().employeeId();
        if (jdbc.queryForList("SELECT id FROM employees WHERE id = ? FOR UPDATE", UUID.class, employeeId).isEmpty()) {
            throw new IllegalArgumentException("Result employee does not exist");
        }
        var requests = jdbc.queryForList(
                "SELECT correlation_id, status, result_event_id FROM account_provisioning_requests WHERE request_id = ? AND employee_id = ?",
                event.requestId(), employeeId);
        if (requests.isEmpty() || !event.correlationId().equals(requests.getFirst().get("correlation_id"))) {
            throw new IllegalArgumentException("Result does not match its account request");
        }
        var previous = jdbc.queryForList("SELECT request_id FROM provisioning_processed_events WHERE event_id = ?",
                UUID.class, event.eventId());
        if (!previous.isEmpty()) {
            if (!event.requestId().equals(previous.getFirst()))
                throw new IllegalArgumentException("Event ID reused across requests");
            return;
        }
        if (!"PENDING".equals(requests.getFirst().get("status"))) {
            throw new IllegalArgumentException("Conflicting terminal result for account request");
        }
        if (success) {
            jdbc.update("""
                    INSERT INTO employee_account_versions (employee_id, version)
                    SELECT ?, 0 WHERE NOT EXISTS (SELECT 1 FROM employee_account_versions WHERE employee_id = ?)
                    """, employeeId, employeeId);
            int applied = jdbc.update(
                    "UPDATE employee_account_versions SET version = ? WHERE employee_id = ? AND version < ?",
                    event.data().accountVersion(), employeeId, event.data().accountVersion());
            if (applied == 1)
                jdbc.update("UPDATE employees SET account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        event.data().accountStatus(), employeeId);
        }
        jdbc.update(
                "UPDATE account_provisioning_requests SET pending_employee_id = NULL, status = ?, error_code = ?, result_event_id = ?, updated_at = CURRENT_TIMESTAMP WHERE request_id = ?",
                success ? "SUCCEEDED" : "FAILED", event.data().errorCode(), event.eventId(), event.requestId());
        jdbc.update("INSERT INTO provisioning_processed_events (event_id, request_id) VALUES (?, ?)", event.eventId(),
                event.requestId());
    }
}
