package com.example.authservice.activation;

import com.example.authservice.entity.User;
import com.example.authservice.exception.GlobalException;
import com.example.authservice.exception.LoginThrottledException;
import com.example.authservice.provisioning.EventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationService {
    private final JdbcTemplate jdbc;
    private final ActivationSettings settings;
    private final ActivationTokens tokens;
    private final PasswordEncoder passwords;
    private final EventOutbox events;
    private final ObjectMapper mapper;
    @Value("${hrm.events.lifecycle-topic:hrm.auth.account-lifecycle.v1}") private String lifecycleTopic;

    public record Invitation(UUID invitationId, String deliveryStatus, OffsetDateTime expiresAt) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void inviteNewAccount(User user) {
        if (settings.enabled) issue(user.getId(), user.getEmail());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public Invitation resend(UUID employeeId) {
        requireEnabled();
        var users = jdbc.queryForList("SELECT id, email, activation_pending FROM users WHERE employee_id = ? FOR UPDATE", employeeId);
        if (users.isEmpty()) throw new GlobalException("Account not found", HttpStatus.NOT_FOUND);
        var user = users.getFirst();
        if (!Boolean.TRUE.equals(user.get("activation_pending")))
            throw new GlobalException("Account is not awaiting activation", HttpStatus.CONFLICT);
        long userId = ((Number) user.get("id")).longValue();
        var recent = jdbc.queryForObject("SELECT COUNT(*) FROM account_activation_tokens WHERE user_id = ? AND created_at > ?",
                Integer.class, userId, now().minus(settings.cooldown));
        if (recent != null && recent > 0) throw new LoginThrottledException(settings.cooldown.toSeconds());
        return issue(userId, (String) user.get("email"));
    }

    public record DeliveryStatus(UUID invitationId, String deliveryStatus, OffsetDateTime expiresAt,
                                 OffsetDateTime sentAt, int attempts, String errorCode) {}

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public DeliveryStatus latest(UUID employeeId) {
        requireEnabled();
        var results = jdbc.query("""
                SELECT t.id, m.status, t.expires_at, m.sent_at, m.attempts, m.last_error
                FROM users u JOIN account_activation_tokens t ON t.user_id = u.id
                JOIN activation_mail_outbox m ON m.id = t.id WHERE u.employee_id = ?
                ORDER BY t.created_at DESC LIMIT 1
                """, (rs, row) -> new DeliveryStatus(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class), rs.getInt(5), rs.getString(6)), employeeId);
        if (results.isEmpty()) throw new GlobalException("Activation invitation not found", HttpStatus.NOT_FOUND);
        return results.getFirst();
    }

    private Invitation issue(long userId, String email) {
        var now = now();
        UUID id = UUID.randomUUID();
        jdbc.update("UPDATE account_activation_tokens SET revoked_at = ? WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL", now, userId);
        jdbc.update("""
                UPDATE activation_mail_outbox SET status = 'CANCELLED', claim_token = NULL, lease_until = NULL
                WHERE status = 'PENDING' AND id IN (SELECT id FROM account_activation_tokens WHERE user_id = ?)
                """, userId);
        jdbc.update("INSERT INTO account_activation_tokens (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, tokens.hash(tokens.derive(id)), now, now.plus(settings.ttl));
        jdbc.update("INSERT INTO activation_mail_outbox (id, recipient, sender, activation_url) VALUES (?, ?, ?, ?)",
                id, email, settings.sender, settings.frontendUrl);
        return new Invitation(id, "PENDING", now.plus(settings.ttl));
    }

    @Transactional
    public void activate(String token, String password) {
        requireEnabled();
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        if (password == null || password.length() < 12 || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new GlobalException("Password must contain at least 12 characters and at most 72 UTF-8 bytes", HttpStatus.BAD_REQUEST);
        String hash = tokens.hash(token);
        var owners = jdbc.queryForList("SELECT user_id FROM account_activation_tokens WHERE token_hash = ?", Long.class, hash);
        if (owners.isEmpty()) throw invalidToken();
        // Same lock order as resend: account first, then invitation. One successful use even under concurrency.
        long userId = owners.getFirst();
        var users = jdbc.queryForList("SELECT employee_id, activation_pending FROM users WHERE id = ? FOR UPDATE", userId);
        if (users.isEmpty() || !Boolean.TRUE.equals(users.getFirst().get("activation_pending"))) throw invalidToken();
        var now = now();
        int consumed = jdbc.update("""
                UPDATE account_activation_tokens SET used_at = ? WHERE token_hash = ?
                  AND used_at IS NULL AND revoked_at IS NULL AND expires_at > ?
                """, now, hash, now);
        if (consumed != 1) throw invalidToken();
        jdbc.update("UPDATE users SET password_hash = ?, activation_pending = FALSE, is_active = TRUE, updated_at = ? WHERE id = ?",
                passwords.encode(password), now, userId);
        jdbc.update("UPDATE account_activation_tokens SET revoked_at = ? WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL", now, userId);
        jdbc.update("UPDATE activation_mail_outbox SET status = 'CANCELLED', claim_token = NULL, lease_until = NULL WHERE status = 'PENDING' AND id IN (SELECT id FROM account_activation_tokens WHERE user_id = ?)", userId);
        UUID employeeId = (UUID) users.getFirst().get("employee_id");
        jdbc.update("UPDATE account_link_versions SET version = version + 1 WHERE employee_id = ?", employeeId);
        Long version = jdbc.queryForObject("SELECT version FROM account_link_versions WHERE employee_id = ?", Long.class, employeeId);
        UUID eventId = UUID.randomUUID();
        events.append(eventId, lifecycleTopic, employeeId, mapper.writeValueAsString(Map.of(
                "eventId", eventId, "eventType", "AccountStatusChanged", "schemaVersion", 1,
                "occurredAt", now.toInstant(), "producer", "auth-service",
                "data", Map.of("employeeId", employeeId, "accountStatus", "ACTIVE", "accountVersion", version))));
    }

    private void requireEnabled() {
        if (!settings.enabled) throw new GlobalException("Account activation is disabled", HttpStatus.SERVICE_UNAVAILABLE);
    }
    private GlobalException invalidToken() { return new GlobalException("Invalid or expired activation token", HttpStatus.BAD_REQUEST); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
