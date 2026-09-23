package com.example.authservice;

import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.repository.RefreshSessionsRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.request.RegisterRequest;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrm_roles_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false",
        "auth.login-throttle.account-max-attempts=100",
        "auth.login-throttle.ip-max-attempts=1000"
})
@AutoConfigureMockMvc
class HrmRolesApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private RefreshSessionsRepository sessions;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        users.deleteAll();
        admin = new User();
        admin.setEmail("admin-roles@example.com");
        admin.setPasswordHash("unused-in-token-authentication-tests");
        admin.setActive(true);
        admin.setRoles(Set.of(UserRole.ADMIN));
        admin = users.saveAndFlush(admin);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @Test
    void registrationRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("ADMIN")))
                .andExpect(status().isUnauthorized());
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "HR"}, mode = EnumSource.Mode.EXCLUDE)
    void usersWithoutHrOrAdminCannotCreateAccountsEvenWithAnOldAdminToken(UserRole role) throws Exception {
        admin.setRoles(Set.of(role));
        users.saveAndFlush(admin);
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(registrationBody("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"));
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"EMPLOYEE", "MANAGER"})
    void serviceAlsoRejectsRegistrationWithoutHrOrAdmin(UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        try {
            assertThrows(AccessDeniedException.class, () -> authService.register(
                    new RegisterRequest("employee@example.com", "password123", Set.of(UserRole.EMPLOYEE))));
            assertEquals(1, users.count());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"EMPLOYEE", "MANAGER", "HR", "ADMIN"})
    void adminCreatesEachHrmRoleAndRoleFlowsThroughLoginRefreshAndMe(UserRole role) throws Exception {
        register(registrationBody(role.name()));
        User user = users.findByEmail("employee@example.com").orElseThrow();
        assertEquals(Set.of(role), user.getRoles());
        assertNull(user.getLastLoginAt());
        assertTrue(passwordEncoder.matches("password123", user.getPasswordHash()));

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(role.name())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = mapper.readTree(login).path("data").path("accessToken").asText();
        String refresh = mapper.readTree(login).path("data").path("refreshToken").asText();
        assertEquals(List.of(role.name()), jwtService.parseToken(access).get("roles", List.class));
        assertFalse(jwtService.parseToken(access).containsKey("role"));
        var lastLogin = users.findById(user.getId()).orElseThrow().getLastLoginAt();
        assertNotNull(lastLogin);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value(role.name()))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        assertEquals(lastLogin, users.findById(user.getId()).orElseThrow().getLastLoginAt());

        // A newly minted access token must reflect the current account role, not the old access token.
        user = users.findById(user.getId()).orElseThrow();
        user.setRoles(Set.of(UserRole.EMPLOYEE));
        users.saveAndFlush(user);
        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of("EMPLOYEE"), jwtService.parseToken(mapper.readTree(rotated).path("accessToken").asText())
                .get("roles", List.class));
        assertEquals(lastLogin, users.findById(user.getId()).orElseThrow().getLastLoginAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"roles\":null}"})
    void omittedOrNullRolesDefaultToEmployee(String fields) throws Exception {
        var body = mapper.createObjectNode().put("email", "employee@example.com").put("password", "password123");
        if (fields.contains("roles")) {
            body.putNull("roles");
        }
        register(mapper.writeValueAsString(body));
        assertEquals(Set.of(UserRole.EMPLOYEE), users.findByEmail("employee@example.com").orElseThrow().getRoles());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "GUEST", "SUPER_ADMIN"})
    void rejectsUnsupportedRoles(String role) throws Exception {
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(registrationBody(role)))
                .andExpect(status().isBadRequest());
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[null]", "[\"EMPLOYEE\",null]", "\"EMPLOYEE\"", "[\"HR\",\"UNKNOWN\"]"})
    void rejectsEmptyOrMalformedRoleSets(String roles) throws Exception {
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"employee@example.com\",\"password\":\"password123\",\"roles\":" + roles + "}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, users.count());
    }

    @Test
    void multipleRolesPersistAndFlowThroughLoginMeAndRefresh() throws Exception {
        register(registrationBody("EMPLOYEE", "HR", "HR"));
        User user = users.findByEmail("employee@example.com").orElseThrow();
        assertEquals(Set.of(UserRole.EMPLOYEE, UserRole.HR), user.getRoles());

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = mapper.readTree(login).path("data").path("accessToken").asText();
        String refresh = mapper.readTree(login).path("data").path("refreshToken").asText();
        assertEquals(List.of("EMPLOYEE", "HR"), jwtService.parseToken(access).get("roles", List.class));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("EMPLOYEE", "HR")))
                .andExpect(jsonPath("$.role").doesNotExist());

        user.setRoles(Set.of(UserRole.EMPLOYEE, UserRole.MANAGER));
        users.saveAndFlush(user);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("EMPLOYEE", "MANAGER")));
        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of("EMPLOYEE", "MANAGER"), jwtService.parseToken(mapper.readTree(rotated).path("accessToken").asText())
                .get("roles", List.class));
    }

    @Test
    void inactiveAccountCannotUseExistingAccessOrRefreshTokens() throws Exception {
        // Establish a real session using the existing service within its transaction.
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        users.saveAndFlush(admin);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", admin.getEmail(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshToken = mapper.readTree(login).path("data").path("refreshToken").asText();
        admin = users.findById(admin.getId()).orElseThrow();
        admin.setActive(false);
        users.saveAndFlush(admin);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(registrationBody("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isForbidden());
        assertEquals(1, sessions.count());
        assertNull(sessions.findAll().getFirst().getRevokedAt());
    }

    @Test
    void deletedAccountCannotUseOldToken() throws Exception {
        users.delete(admin);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"HR", "ADMIN"})
    void eitherHrOrAdminAmongMultipleRolesCanCreateAccounts(UserRole role) throws Exception {
        admin.setRoles(Set.of(UserRole.EMPLOYEE, role));
        users.saveAndFlush(admin);
        register(registrationBody("EMPLOYEE", "MANAGER"));
        assertEquals(Set.of(UserRole.EMPLOYEE, UserRole.MANAGER),
                users.findByEmail("employee@example.com").orElseThrow().getRoles());

        admin.setRoles(Set.of(UserRole.EMPLOYEE, UserRole.MANAGER));
        users.saveAndFlush(admin);
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(registrationBody("ADMIN")))
                .andExpect(status().isForbidden());
    }

    private String registrationBody(String... roles) {
        return mapper.writeValueAsString(Map.of("email", "employee@example.com", "password", "password123", "roles", roles));
    }

    private void register(String body) throws Exception {
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
