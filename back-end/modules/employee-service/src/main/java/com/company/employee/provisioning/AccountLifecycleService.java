package com.company.employee.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {
    private final JdbcTemplate jdbc;
    public record Event(UUID eventId, String eventType, int schemaVersion, Instant occurredAt, String producer, Data data) {}
    public record Data(UUID employeeId, String accountStatus, Long accountVersion) {}
    @Transactional
    public void apply(Event event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null || event.schemaVersion() != 1
                || !"AccountStatusChanged".equals(event.eventType()) || !"auth-service".equals(event.producer())
                || event.data() == null || event.data().employeeId() == null || event.data().accountVersion() == null
                || event.data().accountVersion() < 1 || event.data().accountStatus() == null
                || !Set.of("ACTIVE", "DISABLED", "SUSPENDED", "PENDING_ACTIVATION").contains(event.data().accountStatus()))
            throw new IllegalArgumentException("Invalid account lifecycle event");
        var data = event.data();
        if (jdbc.queryForList("SELECT id FROM employees WHERE id = ? FOR UPDATE", UUID.class, data.employeeId()).isEmpty())
            throw new IllegalArgumentException("Lifecycle employee does not exist");
        jdbc.update("""
                INSERT INTO employee_account_versions (employee_id, version)
                SELECT ?, 0 WHERE NOT EXISTS (SELECT 1 FROM employee_account_versions WHERE employee_id = ?)
                """, data.employeeId(), data.employeeId());
        // Monotonic versions also handle lifecycle arriving before the AccountCreated result.
        int changed = jdbc.update("UPDATE employee_account_versions SET version = ? WHERE employee_id = ? AND version < ?",
                data.accountVersion(), data.employeeId(), data.accountVersion());
        if (changed == 1) jdbc.update("UPDATE employees SET employee_account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                data.accountStatus(), data.employeeId());
    }
}
