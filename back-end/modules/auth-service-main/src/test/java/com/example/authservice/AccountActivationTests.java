package com.example.authservice;

import com.example.authservice.activation.*;
import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.exception.GlobalException;
import com.example.authservice.provisioning.AccountProvisioningService;
import com.example.authservice.provisioning.EmployeeAccountRequested;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"hrm.events.enabled=true", "hrm.events.publisher-enabled=false", "hrm.events.listeners-enabled=false",
        "auth.activation.enabled=true", "auth.activation.worker-enabled=false", "auth.activation.token-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "auth.activation.resend-api-key=test-only-not-a-real-key", "auth.activation.mail-from=HRM <hrm@example.com>",
        "spring.datasource.url=jdbc:h2:mem:activation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AccountActivationTests {
    @Autowired AccountProvisioningService provisioning;
    @Autowired ActivationService activation;
    @Autowired ActivationTokens tokens;
    @Autowired ActivationMailQueue queue;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    UUID employeeId;
    UUID invitationId;
    final String password = "My activation password 2026!";

    @BeforeEach
    void prepare() throws Exception {
        SecurityContextHolder.clearContext();
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/001_auth_schema.sql"));
        }
        for (String table : List.of("activation_mail_outbox", "account_activation_tokens", "event_outbox", "account_provisioning_results",
                "account_link_versions", "refresh_sessions", "user_roles", "users")) jdbc.update("DELETE FROM " + table);
        employeeId = UUID.randomUUID();
        provisioning.process(EmployeeAccountRequested.create(UUID.randomUUID(), employeeId, "activate@example.com", "42"));
        invitationId = jdbc.queryForObject("SELECT id FROM account_activation_tokens", UUID.class);
    }

    @Test
    void queuesOneMailAndStoresOnlyTokenHashInTheSameTransaction() {
        String token = tokens.derive(invitationId);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM account_activation_tokens", String.class)).isEqualTo(tokens.hash(token)).doesNotContain(token);
        assertThat(jdbc.queryForMap("SELECT * FROM activation_mail_outbox").toString()).doesNotContain(token);
        assertThat(jdbc.queryForObject("SELECT payload FROM event_outbox", String.class)).doesNotContain(token);
        var request = EmployeeAccountRequested.create(UUID.randomUUID(), UUID.randomUUID(), "second@example.com", "42");
        provisioning.process(request); provisioning.process(request);
        assertThat(count("activation_mail_outbox")).isEqualTo(2);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            provisioning.process(EmployeeAccountRequested.create(UUID.randomUUID(), UUID.randomUUID(), "rollback@example.com", "42"));
            status.setRollbackOnly();
        });
        assertThat(users.findByEmail("rollback@example.com")).isEmpty();
        assertThat(count("account_activation_tokens")).isEqualTo(2);
        assertThat(count("activation_mail_outbox")).isEqualTo(2);
    }

    @Test
    void publicPageDoesNotConsumeTokenAndActivationAllowsLoginAndPublishesActiveVersion() throws Exception {
        String token = tokens.derive(invitationId);
        mvc.perform(get("/activate").param("token", token)).andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        assertThat(jdbc.queryForObject("SELECT used_at FROM account_activation_tokens", OffsetDateTime.class)).isNull();
        mvc.perform(post("/api/v1/auth/activate").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("token", token, "password", password))))
                .andExpect(status().isOk());
        var user = users.findByEmail("activate@example.com").orElseThrow();
        assertThat(user.isActive()).isTrue(); assertThat(user.isActivationPending()).isFalse();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        var event = mapper.readTree(jdbc.queryForObject("SELECT payload FROM event_outbox WHERE topic = 'hrm.auth.account-lifecycle.v1'", String.class));
        assertThat(event.path("data").path("accountStatus").asText()).isEqualTo("ACTIVE");
        assertThat(event.path("data").path("accountVersion").asLong()).isEqualTo(2);
        assertThatThrownBy(() -> activation.activate(token, password)).isInstanceOf(GlobalException.class);
    }

    @Test
    void expiredRevokedMalformedAndWeakPasswordRequestsLeaveAccountPending() throws Exception {
        String token = tokens.derive(invitationId);
        mvc.perform(post("/api/v1/auth/activate").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("token", token, "password", "short"))))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> activation.activate(token, "đ".repeat(40))).isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> activation.activate("x".repeat(43), password)).isInstanceOf(GlobalException.class);
        jdbc.update("UPDATE account_activation_tokens SET created_at = ?, expires_at = ?", OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1));
        assertThatThrownBy(() -> activation.activate(token, password)).isInstanceOf(GlobalException.class);
        jdbc.update("UPDATE account_activation_tokens SET expires_at = ?, revoked_at = ?", OffsetDateTime.now().plusDays(1), OffsetDateTime.now());
        assertThatThrownBy(() -> activation.activate(token, password)).isInstanceOf(GlobalException.class);
        assertThat(users.findByEmail("activate@example.com").orElseThrow().isActive()).isFalse();
        assertThat(jdbc.queryForObject("SELECT used_at FROM account_activation_tokens", OffsetDateTime.class)).isNull();
    }

    @Test
    void concurrentActivationHasOnlyOneWinner() throws Exception {
        String token = tokens.derive(invitationId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call = () -> { try { activation.activate(token, password); return true; } catch (GlobalException e) { return false; } };
            var first = executor.submit(call); var second = executor.submit(call);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE topic = 'hrm.auth.account-lifecycle.v1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void resendRequiresHrOrAdminAndRevokesPreviousInvitation() throws Exception {
        mvc.perform(post("/api/v1/auth/activation-invitations/" + employeeId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/activation-invitations/" + employeeId).header("Authorization", bearer(UserRole.EMPLOYEE))).andExpect(status().isForbidden());
        String hr = bearer(UserRole.HR);
        mvc.perform(get("/api/v1/auth/activation-invitations/" + employeeId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/activation-invitations/" + employeeId).header("Authorization", hr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryStatus").value("PENDING"))
                .andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(post("/api/v1/auth/activation-invitations/" + employeeId).header("Authorization", hr))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        jdbc.update("UPDATE account_activation_tokens SET created_at = ?", OffsetDateTime.now().minusMinutes(2));
        mvc.perform(post("/api/v1/auth/activation-invitations/" + employeeId).header("Authorization", hr))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.deliveryStatus").value("PENDING"));
        assertThatThrownBy(() -> activation.activate(tokens.derive(invitationId), password)).isInstanceOf(GlobalException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM activation_mail_outbox WHERE id = ?", String.class, invitationId)).isEqualTo("CANCELLED");
        UUID next = jdbc.queryForObject("SELECT id FROM account_activation_tokens WHERE revoked_at IS NULL", UUID.class);
        activation.activate(tokens.derive(next), password);
    }

    @Test
    void queueRetriesUsesLeasesAndCancelsExpiredMail() {
        var first = queue.claim(); assertThat(first).isNotNull(); assertThat(queue.claim()).isNull();
        jdbc.update("UPDATE activation_mail_outbox SET lease_until = ?", OffsetDateTime.now().minusMinutes(3));
        var second = queue.claim();
        queue.sent(first, "stale-worker");
        assertThat(jdbc.queryForObject("SELECT status FROM activation_mail_outbox", String.class)).isEqualTo("PENDING");
        queue.failed(second, "RESEND_HTTP_429", true, 120);
        assertThat(queue.claim()).isNull();
        jdbc.update("UPDATE activation_mail_outbox SET next_attempt_at = ?", OffsetDateTime.now().minusSeconds(1));
        var third = queue.claim(); assertThat(third.id()).isEqualTo(first.id());
        queue.sent(third, "provider-id");
        assertThat(jdbc.queryForObject("SELECT status FROM activation_mail_outbox", String.class)).isEqualTo("SENT");
        assertThat(queue.claim()).isNull();
    }

    @Test
    void expiredInvitationsAndDeliveryWindowNeverSend() {
        jdbc.update("UPDATE account_activation_tokens SET created_at = ?, expires_at = ?", OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1));
        assertThat(queue.claim()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM activation_mail_outbox", String.class)).isEqualTo("CANCELLED");
        jdbc.update("UPDATE activation_mail_outbox SET status = 'PENDING'");
        jdbc.update("UPDATE account_activation_tokens SET expires_at = ?", OffsetDateTime.now().plusDays(1));
        assertThat(queue.claim()).isNull();
        assertThat(jdbc.queryForObject("SELECT last_error FROM activation_mail_outbox", String.class)).isEqualTo("DELIVERY_WINDOW_EXPIRED");
    }

    @Test
    void rollbackRestoresUnusedTokenAndInactiveAccount() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            activation.activate(tokens.derive(invitationId), password); status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT used_at FROM account_activation_tokens", OffsetDateTime.class)).isNull();
        assertThat(users.findByEmail("activate@example.com").orElseThrow().isActive()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE topic = 'hrm.auth.account-lifecycle.v1'", Integer.class)).isZero();
    }

    private String bearer(UserRole role) {
        var user = new User(); user.setEmployeeId(UUID.randomUUID()); user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setPasswordHash("unused"); user.setRoles(Set.of(role)); user.setActive(true);
        return "Bearer " + jwt.generateAccessToken(users.saveAndFlush(user));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
}
