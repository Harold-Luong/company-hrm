package com.example.authservice;

import com.example.authservice.repository.RefreshSessionsRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:browser_cookie_test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BrowserCookieTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RefreshSessionsRepository sessions;
    @Autowired PasswordEncoder encoder;
    private static final String BASE = "/api/v1/auth";
    private static final String REFRESH = "__Host-hrm-refresh";
    private static final String CSRF = "__Host-hrm-csrf";
    private static final String LOGIN = "{\"email\":\"cookie@example.com\",\"password\":\"Password123!\"}";

    @BeforeEach
    void prepare() {
        sessions.deleteAll();
        users.deleteAll();
        User user = new User();
        user.setEmployeeId(UUID.randomUUID());
        user.setEmail("cookie@example.com");
        user.setPasswordHash(encoder.encode("Password123!"));
        user.setActive(true);
        user.setRoles(Set.of(UserRole.EMPLOYEE));
        users.saveAndFlush(user);
    }

    record Csrf(Cookie cookie, String token) {}
    private Csrf csrf() throws Exception {
        var response = mvc.perform(get(BASE + "/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.sessionPresent").value(false))
                .andReturn().getResponse();
        Cookie cookie = response.getCookie(CSRF);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        return new Csrf(cookie, mapper.readTree(response.getContentAsString()).get("token").asString());
    }
    private MockHttpServletResponse login(Csrf csrf) throws Exception {
        return mvc.perform(post(BASE + "/login").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json").content(LOGIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "refresh", "logout", "logout-all"})
    void cookieEndpointsRejectMissingAndWrongCsrf(String endpoint) throws Exception {
        var csrf = csrf();
        var loggedIn = login(csrf);
        Cookie refresh = loggedIn.getCookie(REFRESH);
        String access = mapper.readTree(loggedIn.getContentAsString()).path("data").path("accessToken").asString();
        for (String header : new String[]{"", "incorrect"}) {
            var request = post(BASE + "/" + endpoint).cookie(csrf.cookie(), refresh)
                    .header("Authorization", "Bearer " + access).contentType("application/json").content(LOGIN);
            if (!header.isEmpty()) request.header("X-XSRF-TOKEN", header);
            mvc.perform(request).andExpect(status().isForbidden());
        }
        assertThat(sessions.findAll()).allMatch(session -> session.getRevokedAt() == null);
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test
    void loginRefreshAndLogoutUseOnlyHttpOnlyCookies() throws Exception {
        var csrf = csrf();
        var loggedIn = login(csrf);
        Cookie first = loggedIn.getCookie(REFRESH);
        assertThat(first.isHttpOnly()).isTrue();
        assertThat(first.getSecure()).isTrue();
        assertThat(first.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(first.getDomain()).isNull();
        assertThat(first.getPath()).isEqualTo("/");
        assertThat(first.getMaxAge()).isPositive();
        // A valid token in JSON alone is never a credential for the browser API.
        mvc.perform(post(BASE + "/refresh").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("refreshToken", first.getValue()))))
                .andExpect(status().isUnauthorized());
        // Body-only logout must not revoke a session whose cookie was not sent.
        mvc.perform(post(BASE + "/logout").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("refreshToken", first.getValue()))))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/csrf").cookie(first, csrf.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionPresent").value(true));
        mvc.perform(post(BASE + "/refresh").cookie(first).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isForbidden());
        var rotated = mvc.perform(post(BASE + "/refresh").cookie(csrf.cookie(), first)
                .header("X-XSRF-TOKEN", csrf.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn().getResponse();
        Cookie replacement = rotated.getCookie(REFRESH);
        assertThat(replacement.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(post(BASE + "/refresh").cookie(csrf.cookie(), first).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
        var logout = mvc.perform(post(BASE + "/logout").cookie(csrf.cookie(), replacement)
                .header("X-XSRF-TOKEN", csrf.token())).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(logout.getCookie(REFRESH).getMaxAge()).isZero();
        assertThat(logout.getCookie(REFRESH).getPath()).isEqualTo(first.getPath());
        mvc.perform(post(BASE + "/refresh").cookie(csrf.cookie(), replacement).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%6cogin", "%72efresh", "%6cogout", "%6cogout-all"})
    void encodedCookieEndpointsStillRequireCsrf(String endpoint) throws Exception {
        var csrf = csrf();
        var loggedIn = login(csrf);
        String access = mapper.readTree(loggedIn.getContentAsString()).path("data").path("accessToken").asString();
        mvc.perform(post(java.net.URI.create(BASE + "/" + endpoint))
                .cookie(csrf.cookie(), loggedIn.getCookie(REFRESH))
                .header("Authorization", "Bearer " + access)
                .contentType("application/json").content(LOGIN))
                .andExpect(status().isForbidden());
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(sessions.findAll()).allMatch(session -> session.getRevokedAt() == null);
    }

    @Test
    void logoutAllClearsCookieAndInvalidatesAccess() throws Exception {
        var csrf = csrf();
        var loggedIn = login(csrf);
        String access = mapper.readTree(loggedIn.getContentAsString()).path("data").path("accessToken").asString();
        var result = mvc.perform(post(BASE + "/logout-all").cookie(csrf.cookie(), loggedIn.getCookie(REFRESH))
                .header("X-XSRF-TOKEN", csrf.token()).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(result.getCookie(REFRESH).getMaxAge()).isZero();
        mvc.perform(get(BASE + "/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLogoutCookieIsStillRemoved() throws Exception {
        var csrf = csrf();
        var result = mvc.perform(post(BASE + "/logout").cookie(csrf.cookie(), new Cookie(REFRESH, "invalid"))
                .header("X-XSRF-TOKEN", csrf.token())).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(result.getCookie(REFRESH).getMaxAge()).isZero();
    }
}
