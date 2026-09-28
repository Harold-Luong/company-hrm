package com.company.employee.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventOutbox {
    private final JdbcTemplate jdbc;
    public record Message(UUID eventId, String topic, String messageKey, String payload, UUID claimToken) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, String topic, UUID employeeId, String payload) {
        jdbc.update("INSERT INTO event_outbox (event_id, topic, message_key, payload) VALUES (?, ?, ?, ?)",
                eventId, topic, employeeId.toString(), payload);
    }

    // A short DB transaction claims one row; network I/O happens after this method commits.
    @Transactional
    public Message claim() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> ids = jdbc.query("""
                SELECT event_id FROM event_outbox WHERE sent_at IS NULL AND next_attempt_at <= ?
                  AND (lease_until IS NULL OR lease_until <= ?) ORDER BY created_at LIMIT 20
                """, (rs, row) -> rs.getObject(1, UUID.class), now, now);
        for (UUID id : ids) {
            UUID token = UUID.randomUUID();
            int updated = jdbc.update("""
                    UPDATE event_outbox SET claim_token = ?, lease_until = ?, attempts = attempts + 1
                    WHERE event_id = ? AND sent_at IS NULL AND next_attempt_at <= ?
                      AND (lease_until IS NULL OR lease_until <= ?)
                    """, token, now.plusMinutes(2), id, now, now);
            if (updated == 1) {
                return jdbc.queryForObject("SELECT topic, message_key, payload FROM event_outbox WHERE event_id = ?",
                        (rs, row) -> new Message(id, rs.getString(1), rs.getString(2), rs.getString(3), token), id);
            }
        }
        return null;
    }

    @Transactional
    public void sent(Message message) {
        jdbc.update("""
                UPDATE event_outbox SET sent_at = ?, claim_token = NULL, lease_until = NULL
                WHERE event_id = ? AND claim_token = ? AND lease_until > ?
                """, OffsetDateTime.now(ZoneOffset.UTC), message.eventId(), message.claimToken(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void retry(Message message) {
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM event_outbox WHERE event_id = ?", Integer.class, message.eventId());
        long delay = Math.min(300, 1L << Math.min(attempts, 8));
        jdbc.update("""
                UPDATE event_outbox SET next_attempt_at = ?, claim_token = NULL, lease_until = NULL
                WHERE event_id = ? AND claim_token = ?
                """, OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delay), message.eventId(), message.claimToken());
    }
}
