package com.example.authservice.provisioning;

import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountProvisioningService {
    private final com.example.authservice.activation.ActivationService activation;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final EventOutbox outbox;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final PasswordEncoder passwords;
    private final PlatformTransactionManager transactionManager;
    @Value("${hrm.events.result-topic:hrm.auth.account-results.v1}") private String topic;

    public void process(EmployeeAccountRequested event) {
        if (event == null || !validator.validate(event).isEmpty() || event.schemaVersion() != 1
                || !"EmployeeAccountRequested".equals(event.eventType()) || !"employee-service".equals(event.producer())) {
            throw new IllegalArgumentException("Invalid account request envelope");
        }
        var transaction = new TransactionTemplate(transactionManager);
        // A unique conflict aborts the whole transaction. Retry in a fresh transaction,
        // where the winning Account/request can be read and a stable result recorded.
        for (int attempt = 0; ; attempt++) {
            try {
                transaction.executeWithoutResult(status -> createOrReject(event));
                return;
            } catch (DataIntegrityViolationException exception) {
                if (attempt >= 2) throw exception;
            }
        }
    }

    private void createOrReject(EmployeeAccountRequested event) {
        String fingerprint = fingerprint(event);
        var previous = jdbc.queryForList("SELECT request_id, payload_hash FROM account_provisioning_results WHERE request_id = ? OR event_id = ?",
                event.requestId(), event.eventId());
        if (!previous.isEmpty()) {
            if (previous.size() != 1 || !event.requestId().equals(previous.getFirst().get("request_id"))
                    || !fingerprint.equals(previous.getFirst().get("payload_hash"))) {
                throw new IllegalArgumentException("Account request identity reused with different data");
            }
            return; // Account and result Outbox were committed together on the first delivery.
        }
        UUID employeeId = event.data().employeeId();
        String email = event.data().email().strip().toLowerCase(Locale.ROOT);
        String error = users.existsByEmployeeId(employeeId) ? "ACCOUNT_ALREADY_EXISTS"
                : users.existsByEmail(email) ? "EMAIL_ALREADY_USED" : null;
        Long version = null;
        if (error == null) {
            User user = new User();
            user.setEmployeeId(employeeId);
            user.setEmail(email);
            user.setActive(false);
            user.setActivationPending(true);
            // Unrecoverable per-account secret, never sent or used as a login credential.
            // The activation flow must replace this hash before allowing login.
            user.setPasswordHash(passwords.encode(UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "")));
            user.setRoles(Set.of(UserRole.EMPLOYEE));
            users.saveAndFlush(user);
            activation.inviteNewAccount(user);
            var versions = jdbc.queryForList("SELECT version FROM account_link_versions WHERE employee_id = ? FOR UPDATE", Long.class, employeeId);
            if (versions.isEmpty()) {
                version = 1L;
                jdbc.update("INSERT INTO account_link_versions (employee_id, version) VALUES (?, ?)", employeeId, version);
            } else {
                version = versions.getFirst() + 1;
                jdbc.update("UPDATE account_link_versions SET version = ? WHERE employee_id = ?", version, employeeId);
            }
        }
        var result = AccountProvisioningResult.of(event, error, version);
        String resultJson = mapper.writeValueAsString(result);
        jdbc.update("""
                INSERT INTO account_provisioning_results
                (request_id, event_id, employee_id, payload_hash, result_payload) VALUES (?, ?, ?, ?, ?)
                """, event.requestId(), event.eventId(), employeeId, fingerprint, resultJson);
        outbox.append(result.eventId(), topic, employeeId, resultJson);
    }

    private String fingerprint(EmployeeAccountRequested event) {
        // Ignore the transport event ID; the business request must retain the same content.
        var content = java.util.List.of(event.requestId().toString(), event.correlationId().toString(),
                event.data().employeeId().toString(), event.data().email(), event.data().requestedBy());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
