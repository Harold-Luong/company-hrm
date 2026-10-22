package com.company.attendance.notifications;

import com.company.attendance.exception.ExceptionMessages;
import com.company.attendance.entity.NotificationOutboxEvent;
import com.company.attendance.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationOutbox {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutbox.class);
    private final NotificationOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean enabled;
    public NotificationOutbox(NotificationOutboxRepository outbox, KafkaTemplate<String, String> kafka,
            org.springframework.transaction.PlatformTransactionManager manager, ObjectMapper mapper, Clock clock,
            @Value("${hrm.notifications.enabled:false}") boolean enabled) {
        this.outbox = outbox; this.kafka = kafka; this.transaction = new TransactionTemplate(manager);
        this.mapper = mapper; this.clock = clock; this.enabled = enabled;
    }
    public record Event(UUID eventId, String source, UUID requestId, long version,
            String requesterUserId, UUID employeeId, String actorUserId, String action, String occurredAt) {}
    // Called inside the business transaction; no Kafka network call on the request path.
    public void append(UUID requestId, long version, String requester, UUID employee, String actor, String action) {
        if (!enabled) return;
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(ExceptionMessages.NOTIFICATION_TRANSACTION_REQUIRED);
        var now = clock.instant();
        var event = new Event(UUID.randomUUID(), "ATTENDANCE", requestId, version,
                requester, employee, actor, action, now.toString());
        var row = new NotificationOutboxEvent();
        row.setEventId(event.eventId());
        row.setMessageKey(requestId.toString());
        row.setPayload(mapper.writeValueAsString(event));
        row.setCreatedAt(now);
        row.setNextAttemptAt(now);
        outbox.save(row);
    }
    @Scheduled(fixedDelayString = "${hrm.notifications.publish-delay-ms:1000}")
    public void publish() {
        if (!enabled) return;
        // Lock only an outbox row, not business data. Concurrent publishers skip claimed rows.
        for (int i = 0; i < 20; i++) {
            Boolean found = transaction.execute(status -> {
                var pending = outbox.findNextPendingForUpdate(clock.instant());
                if (pending.isEmpty()) return false;
                var row = pending.get();
                try {
                    kafka.send("hrm.requests.notifications.v1", row.getMessageKey(),
                            row.getPayload()).get(3, TimeUnit.SECONDS);
                    row.setSentAt(clock.instant());
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    row.setAttempts(row.getAttempts() + 1);
                    row.setNextAttemptAt(clock.instant().plusSeconds(10));
                    log.warn("Notification event {} failed on attempt {}; retry scheduled",
                            row.getEventId(), row.getAttempts(), error);
                }
                // The selected entity is managed; JPA flushes its status at commit.
                return true;
            });
            if (!Boolean.TRUE.equals(found) || Thread.currentThread().isInterrupted()) return;
        }
    }
}
