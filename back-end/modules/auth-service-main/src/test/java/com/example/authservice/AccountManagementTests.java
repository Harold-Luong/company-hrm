package com.example.authservice;

import com.example.authservice.accounts.*;
import com.example.authservice.activation.*;
import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.exception.GlobalException;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.request.LoginRequest;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:docs/sql/001_auth_schema.sql",
        "hrm.events.enabled=true", "hrm.events.publisher-enabled=false", "hrm.events.listeners-enabled=false",
        "auth.activation.enabled=true", "auth.activation.worker-enabled=false",
        "auth.activation.token-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "auth.activation.resend-api-key=test-only", "auth.activation.mail-from=HRM <hrm@example.com>",
        "auth.activation.frontend-url=https://hrm.example.com/activate",
        "auth.login-throttle.account-max-attempts=100"
})
@AutoConfigureMockMvc
class AccountManagementTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ActivationTokens activationTokens;
    @Autowired ActivationService activation;
    @MockitoSpyBean AccountAuditRepository audits;
    private static String hash;
    private User admin, target, employee;
    private String adminToken;
    private static final String BASE = "/api/v1/auth/accounts";

    @BeforeEach
    void setup() {
        for (String table : List.of("account_management_audit", "activation_mail_outbox", "account_activation_tokens",
                "refresh_sessions", "event_outbox", "account_link_versions", "user_roles", "users"))
            jdbc.update("DELETE FROM " + table);
        if (hash == null) hash = passwords.encode("Password123!");
        admin = create("admin@example.com", UserRole.ADMIN);
        target = create("hr@example.com", UserRole.HR);
        employee = create("employee@example.com", UserRole.EMPLOYEE);
        adminToken = jwt.generateAccessToken(admin);
    }
    private User create(String email, UserRole role) {
        var user = new User(); user.setEmail(email); user.setEmployeeId(UUID.randomUUID());
        user.setPasswordHash(hash); user.setActive(true); user.setActiveSessionId(UUID.randomUUID());
        user.setRoles(Set.of(role)); return users.saveAndFlush(user);
    }
    private String body(long version, String action) throws Exception {
        return mapper.writeValueAsString(Map.of("version", version, "action", action, "reason", "Reviewed by administrator"));
    }
    private org.springframework.test.web.servlet.ResultActions action(User account, String token, String body) throws Exception {
        return mvc.perform(post(BASE + "/" + account.getId() + "/actions")
                .header("Authorization", "Bearer " + token).contentType("application/json").content(body));
    }

    @Test
    void onlyAdminsCanReadOrMutateAndCookiesCannotAuthorizeManagement() throws Exception {
        for (User user : List.of(target, employee)) {
            String token = jwt.generateAccessToken(user);
            mvc.perform(get(BASE).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/" + target.getId()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/" + target.getId() + "/audit").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            action(target, token, body(0, "DISABLE")).andExpect(status().isForbidden());
        }
        mvc.perform(post(BASE + "/" + target.getId() + "/actions")
                .cookie(new jakarta.servlet.http.Cookie("__Host-hrm-refresh", "anything"))
                .contentType("application/json").content(body(0, "DISABLE"))).andExpect(status().isUnauthorized());
        assertThat(audits.count()).isZero();
    }

    @Test
    void listFiltersPagesAndNeverReturnsCredentials() throws Exception {
        mvc.perform(get(BASE).param("search", "HR@").param("role", "HR").param("status", "ACTIVE").param("size", "1")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].email").value(target.getEmail()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].activeSessionId").doesNotExist());
        for (String size : List.of("0", "101"))
            mvc.perform(get(BASE).param("size", size).header("Authorization", "Bearer " + adminToken)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("page", "-1").header("Authorization", "Bearer " + adminToken)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("status", "UNKNOWN").header("Authorization", "Bearer " + adminToken)).andExpect(status().isBadRequest());
    }

    @Test
    void disableRevokesAccessAndRefreshAndUnlockNeverRestoresOldTokens() throws Exception {
        var login = auth.login(new LoginRequest(target.getEmail(), "Password123!"), "test").data();
        action(target, adminToken, body(0, "DISABLE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + login.accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(login.refreshToken())).isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> auth.login(new LoginRequest(target.getEmail(), "Password123!"), "test")).isInstanceOf(GlobalException.class);
        action(target, adminToken, body(1, "ENABLE")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + login.accessToken())).andExpect(status().isUnauthorized());
        assertThat(auth.login(new LoginRequest(target.getEmail(), "Password123!"), "test").data().accessToken()).isNotBlank();
        mvc.perform(get(BASE + "/" + target.getId() + "/audit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].actorEmail").value(admin.getEmail()))
                .andExpect(jsonPath("$.content[0].action").value("ENABLE"));
        var events = jdbc.queryForList("SELECT payload FROM event_outbox ORDER BY created_at", String.class);
        assertThat(events).hasSize(2);
        var disabled = mapper.readTree(events.get(0)).path("data");
        var enabled = mapper.readTree(events.get(1)).path("data");
        assertThat(disabled.path("accountStatus").asString()).isEqualTo("DISABLED");
        assertThat(enabled.path("accountVersion").asLong()).isGreaterThan(disabled.path("accountVersion").asLong());
    }

    @Test
    void roleChangeAndForcedLogoutRevokeBothTokensWithoutDisablingAccount() throws Exception {
        var old = auth.login(new LoginRequest(target.getEmail(), "Password123!"), "test").data();
        action(target, adminToken, mapper.writeValueAsString(Map.of("version", 0, "action", "CHANGE_ROLES",
                "roles", List.of("EMPLOYEE"), "reason", "Role reduced"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + old.accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(old.refreshToken())).isInstanceOf(GlobalException.class);
        var current = auth.login(new LoginRequest(target.getEmail(), "Password123!"), "test").data();
        assertThat(jwt.parseToken(current.accessToken()).get("roles", List.class)).containsExactly("EMPLOYEE");
        action(target, adminToken, body(1, "REVOKE_SESSIONS")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThatThrownBy(() -> auth.refresh(current.refreshToken())).isInstanceOf(GlobalException.class);
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + current.accessToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSelfChangesStaleWritesAndInvalidCommandsWithoutAudit() throws Exception {
        for (String name : List.of("DISABLE", "ENABLE", "CHANGE_ROLES", "REVOKE_SESSIONS"))
            action(admin, adminToken, body(0, name)).andExpect(status().isConflict());
        action(target, adminToken, body(4, "DISABLE")).andExpect(status().isPreconditionFailed());
        for (String invalid : List.of(
                "{\"version\":0,\"action\":\"UNKNOWN\",\"reason\":\"test\"}",
                "{\"version\":0,\"action\":\"DISABLE\",\"reason\":\" \"}",
                "{\"action\":\"DISABLE\",\"reason\":\"test\"}",
                "{\"version\":0,\"action\":\"CHANGE_ROLES\",\"roles\":[],\"reason\":\"test\"}",
                "{\"version\":0,\"action\":\"CHANGE_ROLES\",\"roles\":[null],\"reason\":\"test\"}",
                "{\"version\":0,\"action\":\"CHANGE_ROLES\",\"roles\":[\"ROOT\"],\"reason\":\"test\"}"))
            action(target, adminToken, invalid).andExpect(status().isBadRequest());
        assertThat(audits.count()).isZero();
        action(target, adminToken, body(0, "DISABLE")).andExpect(status().isOk());
        action(target, adminToken, body(0, "ENABLE")).andExpect(status().isPreconditionFailed());
        assertThat(audits.count()).isEqualTo(1);
    }

    @Test
    void pendingAccountCannotActivateWhileLockedAndUnlockRequiresNewInvitation() throws Exception {
        target.setActive(false); target.setActivationPending(true); users.saveAndFlush(target);
        UUID invitation = UUID.randomUUID();
        jdbc.update("INSERT INTO account_activation_tokens (id,user_id,token_hash,created_at,expires_at) VALUES (?,?,?,CURRENT_TIMESTAMP,DATEADD('HOUR',1,CURRENT_TIMESTAMP))",
                invitation, target.getId(), activationTokens.hash(activationTokens.derive(invitation)));
        action(target, adminToken, body(0, "DISABLE")).andExpect(status().isOk());
        assertThatThrownBy(() -> activation.activate(activationTokens.derive(invitation), "NewPassword123!")).isInstanceOf(GlobalException.class);
        action(target, adminToken, body(1, "ENABLE")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));
        assertThat(users.findById(target.getId()).orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> activation.activate(activationTokens.derive(invitation), "NewPassword123!")).isInstanceOf(GlobalException.class);
    }

    @Test
    void concurrentAdminsCannotRemoveEveryActiveAdministrator() throws Exception {
        target.setRoles(Set.of(UserRole.ADMIN)); users.saveAndFlush(target);
        String otherToken = jwt.generateAccessToken(target);
        var start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return action(target, adminToken, body(0, "DISABLE")).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { start.await(); return action(admin, otherToken, body(0, "DISABLE")).andReturn().getResponse().getStatus(); });
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(users.findAll().stream().filter(u -> u.isActive() && u.getRoles().contains(UserRole.ADMIN)).count()).isEqualTo(1);
        assertThat(audits.count()).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackAccountSessionsAndOutbox() throws Exception {
        var login = auth.login(new LoginRequest(target.getEmail(), "Password123!"), "test").data();
        doThrow(new IllegalStateException("Audit storage unavailable")).when(audits).save(any(AccountAudit.class));
        assertThatThrownBy(() -> action(target, adminToken, body(0, "DISABLE"))).hasRootCauseMessage("Audit storage unavailable");
        var current = users.findById(target.getId()).orElseThrow();
        assertThat(current.isActive()).isTrue();
        assertThat(current.getManagementVersion()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isZero();
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + login.accessToken())).andExpect(status().isNoContent());
        assertThat(auth.refresh(login.refreshToken()).accessToken()).isNotBlank();
    }
}
