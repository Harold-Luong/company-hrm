package com.company.notification;

import com.company.notification.exception.ExceptionMessages;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountResults {
    private final JdbcTemplate jdbc;
    public AccountResults(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Event(UUID eventId, String eventType, int schemaVersion, Instant occurredAt,
                        String producer, UUID correlationId, UUID requestId, Data data) {}
    public record Data(UUID employeeId, String accountStatus, Long accountVersion, String errorCode,
                       String requestedBy) {}
    public record Item(long sequence, UUID requestId, UUID employeeId, String provisioningStatus, String errorCode) {}
    public record Batch(long cursor, List<Item> items) {}

    @Transactional
    public void accept(Event event) {
        if (event == null || event.eventId() == null || event.requestId() == null || event.occurredAt() == null
                || event.schemaVersion() != 1 || !"auth-service".equals(event.producer())
                || event.data() == null || event.data().employeeId() == null
                || !("AccountCreated".equals(event.eventType()) || "AccountCreationFailed".equals(event.eventType())))
            throw new IllegalArgumentException(ExceptionMessages.INVALID_ACCOUNT_RESULT);
        var data = event.data();
        // Old v1 events predate recipient information. Never broadcast them to other reviewers.
        if (data.requestedBy() == null || data.requestedBy().isBlank()) return;
        boolean success = "AccountCreated".equals(event.eventType());
        if (data.requestedBy().length() > 255 || (success
                ? data.accountVersion() == null || data.accountVersion() < 1 || data.errorCode() != null
                    || !"PENDING_ACTIVATION".equals(data.accountStatus())
                : !("ACCOUNT_ALREADY_EXISTS".equals(data.errorCode()) || "EMAIL_ALREADY_USED".equals(data.errorCode()))))
            throw new IllegalArgumentException(ExceptionMessages.INVALID_ACCOUNT_RESULT_DATA);
        // Serialize inserts so stream cursors follow commit order, including across consumer instances.
        jdbc.execute("LOCK TABLE account_result_notifications IN EXCLUSIVE MODE");
        jdbc.update("""
            INSERT INTO account_result_notifications
              (event_id, request_id, employee_id, recipient_user_id, provisioning_status, error_code, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
            """, event.eventId(), event.requestId(), data.employeeId(), data.requestedBy(),
                success ? "SUCCEEDED" : "FAILED", data.errorCode(), java.sql.Timestamp.from(event.occurredAt()));
    }

    public long latest(Notifications.Actor actor) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) FROM account_result_notifications WHERE recipient_user_id = ?",
                Long.class, actor.userId());
    }

    public Batch after(Notifications.Actor actor, long cursor) {
        // HR/ADMIN may only receive results for requests they submitted themselves.
        if (!actor.reviewer()) return new Batch(cursor, List.of());
        var items = jdbc.query("""
            SELECT sequence, request_id, employee_id, provisioning_status, error_code
            FROM account_result_notifications WHERE recipient_user_id = ? AND sequence > ?
            ORDER BY sequence LIMIT 50
            """, (rs, i) -> new Item(rs.getLong("sequence"), rs.getObject("request_id", UUID.class),
                rs.getObject("employee_id", UUID.class), rs.getString("provisioning_status"), rs.getString("error_code")),
                actor.userId(), cursor);
        return new Batch(items.isEmpty() ? cursor : items.getLast().sequence(), items);
    }
}
