package com.example.authservice.accounts;

import com.example.authservice.provisioning.EventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountLifecycleEvents {
    private final JdbcTemplate jdbc;
    private final EventOutbox outbox;
    private final ObjectMapper mapper;
    @Value("${hrm.events.lifecycle-topic:hrm.auth.account-lifecycle.v1}") private String topic;

    // Caller holds the account row lock. Share the same monotonic sequence with activation/provisioning.
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID employeeId, AccountStatus status) {
        jdbc.update("""
                INSERT INTO account_link_versions (employee_id, version)
                SELECT ?, 1 WHERE NOT EXISTS (SELECT 1 FROM account_link_versions WHERE employee_id = ?)
                """, employeeId, employeeId);
        jdbc.update("UPDATE account_link_versions SET version = version + 1 WHERE employee_id = ?", employeeId);
        Long version = jdbc.queryForObject("SELECT version FROM account_link_versions WHERE employee_id = ?", Long.class, employeeId);
        UUID id = UUID.randomUUID();
        outbox.append(id, topic, employeeId, mapper.writeValueAsString(Map.of(
                "eventId", id, "eventType", "AccountStatusChanged", "schemaVersion", 1,
                "occurredAt", Instant.now(), "producer", "auth-service",
                "data", Map.of("employeeId", employeeId, "accountStatus", status.name(), "accountVersion", version))));
    }
}
