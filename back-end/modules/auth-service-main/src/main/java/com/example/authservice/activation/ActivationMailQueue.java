package com.example.authservice.activation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationMailQueue {
    private final JdbcTemplate jdbc;
    public record Mail(UUID id, UUID claim, String recipient, String sender, String frontendUrl, OffsetDateTime expiresAt, int attempts) {}

    @Transactional
    public Mail claim() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        // Stop before Resend's 24-hour idempotency retention expires, even with a longer token TTL.
        jdbc.update("""
                UPDATE activation_mail_outbox SET status = 'CANCELLED', claim_token = NULL, lease_until = NULL
                WHERE status = 'PENDING' AND id IN (SELECT id FROM account_activation_tokens
                    WHERE expires_at <= ? OR used_at IS NOT NULL OR revoked_at IS NOT NULL)
                """, now);
        jdbc.update("""
                UPDATE activation_mail_outbox SET status = 'FAILED', last_error = 'DELIVERY_WINDOW_EXPIRED', claim_token = NULL, lease_until = NULL
                WHERE status = 'PENDING' AND id IN (SELECT id FROM account_activation_tokens WHERE created_at <= ?)
                """, now.minusHours(23));
        var ids = jdbc.queryForList("""
                SELECT id FROM activation_mail_outbox WHERE status = 'PENDING' AND next_attempt_at <= ?
                  AND (lease_until IS NULL OR lease_until <= ?) ORDER BY next_attempt_at LIMIT 20
                """, UUID.class, now, now);
        for (var id : ids) {
            UUID claim = UUID.randomUUID();
            if (jdbc.update("""
                    UPDATE activation_mail_outbox SET claim_token = ?, lease_until = ?, attempts = attempts + 1
                    WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= ? AND (lease_until IS NULL OR lease_until <= ?)
                    """, claim, now.plusMinutes(2), id, now, now) == 1) {
                return jdbc.queryForObject("""
                        SELECT m.recipient, m.sender, m.activation_url, t.expires_at, m.attempts
                        FROM activation_mail_outbox m JOIN account_activation_tokens t ON t.id = m.id WHERE m.id = ?
                        """, (rs, row) -> new Mail(id, claim, rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getInt(5)), id);
            }
        }
        return null;
    }

    @Transactional
    public void sent(Mail mail, String providerId) {
        jdbc.update("""
                UPDATE activation_mail_outbox SET status = 'SENT', sent_at = ?, provider_id = ?,
                    last_error = NULL, claim_token = NULL, lease_until = NULL
                WHERE id = ? AND claim_token = ? AND status = 'PENDING' AND lease_until > ?
                """, OffsetDateTime.now(ZoneOffset.UTC), providerId, mail.id(), mail.claim(), OffsetDateTime.now(ZoneOffset.UTC));
    }
    @Transactional
    public void failed(Mail mail, String safeErrorCode, boolean retryable, long retryAfterSeconds) {
        long delay = Math.max(Math.min(3600, retryAfterSeconds), Math.min(300, 1L << Math.min(mail.attempts(), 8)));
        jdbc.update("""
                UPDATE activation_mail_outbox SET status = ?, last_error = ?, next_attempt_at = ?, claim_token = NULL, lease_until = NULL
                WHERE id = ? AND claim_token = ? AND status = 'PENDING' AND lease_until > ?
                """, retryable ? "PENDING" : "FAILED", safeErrorCode, OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delay),
                mail.id(), mail.claim(), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
