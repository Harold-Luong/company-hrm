package com.company.attendance;

import com.company.attendance.repository.HealthCheckRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceApplicationTests extends JwtTestSupport {
    private static final String HEALTH = "/api/v1/attendance/health-check";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private HealthCheckRepository healthCheckRepository;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtAuthenticationConverter converter;

    @Test
    void bootsJpaAndQueriesDatabaseThroughRepository() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(healthCheckRepository.isDatabaseConnected()).isTrue();
    }

    @Test
    void healthEndpointRequiresBearerAndReturnsDatabaseStatus() throws Exception {
        mvc.perform(get(HEALTH)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mvc.perform(get(HEALTH).header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get(HEALTH)).andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorExposesOnlyMinimalPublicHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims())))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishesOpenApiWithBearerContract() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Attendance Service API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void mapsAuthRolesAndEmployeeIdentity() throws Exception {
        var jwt = decoder.decode(sign(claims().claim("roles", List.of("HR", "EMPLOYEE"))));
        var authentication = converter.convert(jwt);
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities()).extracting(authority -> authority.getAuthority())
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .containsExactlyInAnyOrder("ROLE_HR", "ROLE_EMPLOYEE");
        assertThat(jwt.getClaimAsString("employee_id"))
                .isEqualTo("d38e31b7-0bba-420c-8eaf-50edbe5a61ae");
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "missingExpiration", "futureNotBefore",
            "missingSubject", "missingEmployee", "invalidEmployee", "missingRoles", "invalidRoles",
            "invalidRoleElement", "emptyRoles", "refreshType"})
    void rejectsInvalidClaims(String invalid) throws Exception {
        var claims = claims();
        switch (invalid) {
            case "issuer" -> claims.issuer("untrusted");
            case "audience" -> claims.audience("hrm-api-refresh");
            case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(60)));
            case "missingExpiration" -> claims.expirationTime(null);
            case "futureNotBefore" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(300)));
            case "missingSubject" -> claims.subject(null);
            case "missingEmployee" -> claims.claim("employee_id", null);
            case "invalidEmployee" -> claims.claim("employee_id", "invalid");
            case "missingRoles" -> claims.claim("roles", null);
            case "invalidRoles" -> claims.claim("roles", "ADMIN");
            case "invalidRoleElement" -> claims.claim("roles", List.of(123));
            case "emptyRoles" -> claims.claim("roles", List.of());
            case "refreshType" -> claims.claim("type", "refresh");
            default -> throw new IllegalArgumentException(invalid);
        }
        assertUnauthorized(sign(claims));
    }

    @Test
    void rejectsOtherKeysAlgorithmsUnsignedAndMalformedTokens() throws Exception {
        var forged = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims().build());
        forged.sign(new RSASSASigner(generateKeyPair().getPrivate()));
        assertUnauthorized(forged.serialize());
        var otherAlgorithm = new SignedJWT(new JWSHeader(JWSAlgorithm.RS512), claims().build());
        otherAlgorithm.sign(new RSASSASigner(ACCESS_KEYS.getPrivate()));
        assertUnauthorized(otherAlgorithm.serialize());
        assertUnauthorized(new PlainJWT(claims().build()).serialize());
        assertUnauthorized("not.a.jwt");
    }

    @Test
    void rejectsQueryTokensAndResourcesOutsideAttendance() throws Exception {
        mvc.perform(get(HEALTH).param("access_token", sign(claims())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/employees").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims())))
                .andExpect(status().isForbidden());
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get(HEALTH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().subject("42").issuer("auth-service").audience("hrm-api-access")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("employee_id", "d38e31b7-0bba-420c-8eaf-50edbe5a61ae")
                .claim("roles", List.of("EMPLOYEE"));
    }

    private String sign(JWTClaimsSet.Builder claims) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(ACCESS_KEYS.getPrivate()));
        return jwt.serialize();
    }
}
