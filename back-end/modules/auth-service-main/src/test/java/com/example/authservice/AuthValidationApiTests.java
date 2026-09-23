package com.example.authservice;

import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.service.JwtService;
import com.example.authservice.repository.RefreshSessionsRepository;
import com.example.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthValidationApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private RefreshSessionsRepository sessions;
    @Autowired private JwtService jwtService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        users.deleteAll();
        User admin = new User();
        admin.setEmployeeId(9000L);
        admin.setEmail("admin-validation@example.com");
        admin.setPasswordHash("unused-in-token-authentication-tests");
        admin.setActive(true);
        admin.setRoles(Set.of(UserRole.ADMIN));
        adminToken = jwtService.generateAccessToken(users.saveAndFlush(admin));
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void rejectsInvalidCredentialsBeforeWritingToDatabase(String endpoint, String body, String message) throws Exception {
        if ("register".equals(endpoint)) {
            var fields = (tools.jackson.databind.node.ObjectNode) mapper.readTree(body);
            body = mapper.writeValueAsString(fields.put("employeeId", 1001L));
        }
        mvc.perform(authPost(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value(containsString(message)))
                .andExpect(content().string(not(containsString("secret-password"))));
        assertEquals(1, users.count());
        assertEquals(0, sessions.count());
    }

    static Stream<Arguments> invalidCredentials() {
        return Stream.of("register", "login").flatMap(endpoint -> Stream.of(
                Arguments.of(endpoint, "{}", "email: Email is required; password: Password is required"),
                Arguments.of(endpoint, "{\"password\":\"secret-password\"}", "email: Email is required"),
                Arguments.of(endpoint, "{\"email\":null,\"password\":\"secret-password\"}", "email: Email is required"),
                Arguments.of(endpoint, "{\"email\":\"   \",\"password\":\"secret-password\"}", "email: Email is required"),
                Arguments.of(endpoint, "{\"email\":\"invalid-email\",\"password\":\"secret-password\"}", "email: Email must be valid"),
                Arguments.of(endpoint, "{\"email\":\"" + "a".repeat(256) + "@example.com\",\"password\":\"secret-password\"}",
                        "email: Email must not exceed 255 characters"),
                Arguments.of(endpoint, "{\"email\":\"user@example.com\"}", "password: Password is required"),
                Arguments.of(endpoint, "{\"email\":\"user@example.com\",\"password\":null}", "password: Password is required"),
                Arguments.of(endpoint, "{\"email\":\"user@example.com\",\"password\":\"\"}", "password: Password is required"),
                Arguments.of(endpoint, "{\"email\":\"user@example.com\",\"password\":\"   \"}", "password: Password is required")
        ));
    }

    @ParameterizedTest
    @MethodSource("missingRefreshTokens")
    void validatesRefreshAndLogoutBodies(String endpoint, String body) throws Exception {
        mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("refreshToken: Refresh token is required"));
    }

    static Stream<Arguments> missingRefreshTokens() {
        return Stream.of("refresh", "logout").flatMap(endpoint -> Stream.of(
                "{}", "{\"refreshToken\":null}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"   \"}")
                .map(body -> Arguments.of(endpoint, body)));
    }

    @ParameterizedTest
    @MethodSource("unreadableBodies")
    void returnsConsistentErrorForUnreadableBodies(String endpoint, String body) throws Exception {
        mvc.perform(authPost(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("Request body is missing or contains invalid JSON"))
                .andExpect(content().string(not(containsString("secret-password"))));
    }

    static Stream<Arguments> unreadableBodies() {
        return Stream.of("register", "login", "refresh", "logout").flatMap(endpoint -> {
            String field = endpoint.equals("register") || endpoint.equals("login") ? "password" : "refreshToken";
            return Stream.of("", "null", "{", "[]", "{\"" + field + "\":{\"secret\":\"secret-password\"}}")
                    .map(body -> Arguments.of(endpoint, body));
        });
    }

    @Test
    void validRegistrationAndLoginStillWorkWithTrimmedEmailAndUnmodifiedPassword() throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", "  valid@example.com  ", "password", " password123 ", "employeeId", 1001L));
        mvc.perform(authPost("register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        assertEquals(2, users.count());
        assertEquals(1, sessions.count());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "valid@example.com", "password", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
        mvc.perform(authPost("register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh", "logout"})
    void invalidJwtRemainsAnAuthenticationError(String endpoint) throws Exception {
        mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }

    private MockHttpServletRequestBuilder authPost(String endpoint) {
        var request = post("/api/v1/auth/" + endpoint);
        return "register".equals(endpoint) ? request.header("Authorization", "Bearer " + adminToken) : request;
    }
}
