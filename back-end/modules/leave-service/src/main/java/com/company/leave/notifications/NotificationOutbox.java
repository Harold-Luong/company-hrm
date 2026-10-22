package com.company.leave.notifications;

import com.company.leave.exception.ExceptionMessages;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationOutbox {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final boolean enabled;
    public NotificationOutbox(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
            org.springframework.transaction.PlatformTransactionManager manager, ObjectMapper mapper,
            @Value("${hrm.notifications.enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.kafka = kafka; this.transaction = new TransactionTemplate(manager);
        this.mapper = mapper; this.enabled = enabled;
    }
    public record Event(UUID eventId, String source, UUID requestId, long version,
            String requesterUserId, UUID employeeId, String actorUserId, String action, String occurredAt) {}
    // Called inside the business transaction; no Kafka network call on the request path.
    public void append(UUID requestId, long version, String requester, UUID employee, String actor, String action) {
        if (!enabled) return;
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(ExceptionMessages.NOTIFICATION_TRANSACTION_REQUIRED);
        var event = new Event(UUID.randomUUID(), "LEAVE", requestId, version,
                requester, employee, actor, action, Instant.now().toString());
        jdbc.update("INSERT INTO notification_outbox(event_id, message_key, payload) VALUES (?, ?, ?)",
                event.eventId(), requestId.toString(), mapper.writeValueAsString(event));
    }
    @Scheduled(fixedDelayString = "${hrm.notifications.publish-delay-ms:1000}")
    public void publish() {
        if (!enabled) return;
        // Lock only an outbox row, not business data. Concurrent publishers skip claimed rows.
        for (int i = 0; i < 20; i++) {
            Boolean found = transaction.execute(status -> {
                var rows = jdbc.queryForList("""
                    SELECT event_id, message_key, payload FROM notification_outbox
                    WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                    """);
                if (rows.isEmpty()) return false;
                var row = rows.getFirst();
                try {
                    kafka.send("hrm.requests.notifications.v1", row.get("message_key").toString(),
                            row.get("payload").toString()).get(3, TimeUnit.SECONDS);
                    jdbc.update("UPDATE notification_outbox SET sent_at = CURRENT_TIMESTAMP WHERE event_id = ?", row.get("event_id"));
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    jdbc.update("""
                        UPDATE notification_outbox SET attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '10 seconds' WHERE event_id = ?
                        """, row.get("event_id"));
                }
                return true;
            });
            if (!Boolean.TRUE.equals(found) || Thread.currentThread().isInterrupted()) return;
        }
    }
}
