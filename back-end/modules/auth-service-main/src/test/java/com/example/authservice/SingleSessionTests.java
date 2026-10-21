package com.example.authservice;

import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.exception.GlobalException;
import com.example.authservice.repository.RefreshSessionsRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.request.LoginRequest;
import com.example.authservice.request.RefreshTokenRequest;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:single-session;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "auth.login-throttle.account-max-attempts=100"
})
@AutoConfigureMockMvc
class SingleSessionTests {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired RefreshSessionsRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @MockitoSpyBean JwtService jwt;
    private final LoginRequest credentials = new LoginRequest("single@example.com", "Password@123");

    @BeforeEach
    void account() {
        sessions.deleteAll(); users.deleteAll();
        var user = new User();
        user.setEmail(credentials.email()); user.setEmployeeId(UUID.randomUUID());
        user.setPasswordHash(passwords.encode(credentials.password()));
        user.setActive(true); user.setRoles(Set.of(UserRole.EMPLOYEE));
        users.saveAndFlush(user);
    }

    @Test
    void newLoginRevokesBothTokensAndOldLogoutCannotEndTheNewSession() throws Exception {
        var old = auth.login(credentials, "first").data();
        var current = auth.login(credentials, "second").data();
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + old.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(old.refreshToken())).isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> auth.logout(new RefreshTokenRequest(old.refreshToken())))
                .isInstanceOf(GlobalException.class);
        var staleLogout = UsernamePasswordAuthenticationToken.authenticated(
                jwt.parseToken(old.accessToken()).getSubject(), null, List.of());
        staleLogout.setDetails(UUID.fromString(jwt.parseToken(old.accessToken()).get("sid", String.class)));
        assertThatThrownBy(() -> auth.logoutAll(staleLogout)).isInstanceOf(GlobalException.class);
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + current.accessToken()))
                .andExpect(status().isNoContent());
        assertThat(sessions.findAll().stream().filter(s -> s.getRevokedAt() == null).count()).isEqualTo(1);
    }

    @Test
    void refreshKeepsTheLoginIdentitySoOtherTabsRemainValidAndLogoutRevokesAccess() throws Exception {
        var login = auth.login(credentials, "first").data();
        var rotated = auth.refresh(login.refreshToken());
        assertThat(jwt.parseToken(rotated.accessToken()).get("sid"))
                .isEqualTo(jwt.parseToken(login.accessToken()).get("sid"));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent());
        auth.logout(new RefreshTokenRequest(rotated.refreshToken()));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + rotated.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void failedCredentialsAndFailedTokenIssuanceDoNotRevokeTheExistingLogin() throws Exception {
        var old = auth.login(credentials, "first").data();
        assertThatThrownBy(() -> auth.login(new LoginRequest(credentials.email(), "wrong"), "second"))
                .isInstanceOf(GlobalException.class);
        doThrow(new IllegalStateException("Signing unavailable")).when(jwt).generateAccessToken(any(User.class));
        assertThatThrownBy(() -> auth.login(credentials, "third")).isInstanceOf(IllegalStateException.class);
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + old.accessToken()))
                .andExpect(status().isNoContent());
        assertThat(sessions.findAll()).hasSize(1).allMatch(s -> s.getRevokedAt() == null);
    }

    @Test
    void tokensWithoutLoginIdentityCannotUseTheCompatibilityPath() throws Exception {
        auth.login(credentials, "first");
        var legacy = users.findByEmail(credentials.email()).orElseThrow();
        legacy.setActiveSessionId(null);
        String access = jwt.generateAccessToken(legacy);
        String refresh = jwt.generateRefreshToken(legacy).getToken();
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(refresh)).isInstanceOfSatisfying(GlobalException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void concurrentLoginsLeaveExactlyOneUsableSession() throws Exception {
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> login = () -> { start.await(10, TimeUnit.SECONDS); return auth.login(credentials, UUID.randomUUID().toString()).data().accessToken(); };
            var first = executor.submit(login); var second = executor.submit(login);
            int accepted = 0;
            for (String token : List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))) {
                int code = mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getStatus();
                assertThat(code).isIn(204, 401);
                if (code == 204) accepted++;
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(sessions.findAll().stream().filter(s -> s.getRevokedAt() == null).count()).isEqualTo(1);
        }
    }

    @Test
    void concurrentRefreshCannotRestoreTheLoginReplacedByANewLogin() throws Exception {
        var old = auth.login(credentials, "first").data();
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var refreshing = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try { return auth.refresh(old.refreshToken()).accessToken(); }
                catch (GlobalException error) { assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED); return null; }
            });
            var loggingIn = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return auth.login(credentials, "second").data(); });
            var current = loggingIn.get(15, TimeUnit.SECONDS);
            var refreshedAccess = refreshing.get(15, TimeUnit.SECONDS);
            if (refreshedAccess != null) mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + refreshedAccess))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + current.accessToken()))
                    .andExpect(status().isNoContent());
        }
    }
}
