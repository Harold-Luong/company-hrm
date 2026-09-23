package com.company.employee;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityTests extends JwtTestSupport {
    private static final String EMPLOYEE_ID = "d38e31b7-0bba-420c-8eaf-50edbe5a61ae";

    @Autowired private MockMvc mvc;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtAuthenticationConverter converter;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/employees", "/api/v1/departments", "/api/v1/positions"})
    void requiresTokenOnAllBusinessApis(String path) throws Exception {
        for (var method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            mvc.perform(request(method, path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.status").value(401));
        }
        mvc.perform(get(path + "/" + EMPLOYEE_ID)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/employees/health-check", "/actuator/health", "/actuator/health/db",
            "/openapi/employee-api.yml", "/swagger-ui/index.html"})
    void allowsPublicHealthAndDocumentation(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void swaggerCanLoadItsConfigurationWithoutToken() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/openapi/employee-api.yml"));
    }

    @Test
    void acceptsAuthAccessTokenFormatAndDoesNotCreateSession() throws Exception {
        String token = sign(claims());
        for (String path : List.of("/api/v1/employees", "/api/v1/departments", "/api/v1/positions")) {
            var result = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
        }
        var authentication = converter.convert(decoder.decode(token));
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_HR", "ROLE_EMPLOYEE");
        assertThat(decoder.decode(token).getClaimAsString("employee_id")).isEqualTo(EMPLOYEE_ID);
        mvc.perform(get("/api/v1/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsBearerAuthenticatedWritesWithoutCsrfCookie() throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SEC-" + UUID.randomUUID() + "\",\"name\":\"Security test\"}"))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "missingIssuer", "audience", "missingAudience", "expired", "missingExpiration",
            "futureNotBefore", "missingSubject", "missingEmployee", "invalidEmployee", "missingRoles",
            "invalidRoles", "invalidRoleElement", "emptyRoles", "refreshType"})
    void rejectsInvalidClaimsEvenWithValidSignature(String invalidClaim) throws Exception {
        var claims = claims();
        switch (invalidClaim) {
            case "issuer" -> claims.issuer("untrusted-auth");
            case "missingIssuer" -> claims.issuer(null);
            case "audience" -> claims.audience("hrm-api-refresh");
            case "missingAudience" -> claims.audience((String) null);
            case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120)));
            case "missingExpiration" -> claims.expirationTime(null);
            case "futureNotBefore" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(300)));
            case "missingSubject" -> claims.subject(null);
            case "missingEmployee" -> claims.claim("employee_id", null);
            case "invalidEmployee" -> claims.claim("employee_id", "not-a-uuid");
            case "missingRoles" -> claims.claim("roles", null);
            case "invalidRoles" -> claims.claim("roles", "ADMIN");
            case "invalidRoleElement" -> claims.claim("roles", List.of(123));
            case "emptyRoles" -> claims.claim("roles", List.of());
            case "refreshType" -> claims.claim("type", "refresh");
            default -> throw new IllegalArgumentException(invalidClaim);
        }
        assertUnauthorized(sign(claims));
    }

    @Test
    void rejectsWrongSignatureAndRefreshKey() throws Exception {
        var otherKey = generateKeyPair();
        var forged = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims().build());
        forged.sign(new RSASSASigner(otherKey.getPrivate()));
        assertUnauthorized(forged.serialize());

        var refresh = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims()
                .audience("hrm-api-refresh").claim("type", "refresh")
                .claim("employee_id", null).claim("roles", null).build());
        refresh.sign(new RSASSASigner(otherKey.getPrivate()));
        assertUnauthorized(refresh.serialize());
    }

    @Test
    void rejectsUnsignedHmacAndOtherRsaAlgorithms() throws Exception {
        assertUnauthorized(new PlainJWT(claims().build()).serialize());
        var hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims().build());
        hmac.sign(new MACSigner(new byte[32]));
        assertUnauthorized(hmac.serialize());
        var rsa512 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS512), claims().build());
        rsa512.sign(new RSASSASigner(ACCESS_KEYS.getPrivate()));
        assertUnauthorized(rsa512.serialize());
    }

    @Test
    void rejectsMalformedTamperedAndQueryStringTokens() throws Exception {
        assertUnauthorized("not.a.jwt");
        String token = sign(claims());
        String changedPayload = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),
                claims().subject("999").build()).getPayload().toBase64URL().toString();
        String[] parts = token.split("\\.");
        assertUnauthorized(parts[0] + "." + changedPayload + "." + parts[2]);
        mvc.perform(get("/api/v1/employees").param("access_token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesOtherResourcesEvenWithValidToken() throws Exception {
        mvc.perform(get("/sql/init_employee_schema.sql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/v1/employees").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401));
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().subject("42").issuer("auth-service").audience("hrm-api-access")
                .issueTime(Date.from(Instant.now().minusSeconds(1)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("employee_id", EMPLOYEE_ID).claim("email", "hr@example.com")
                .claim("roles", List.of("HR", "EMPLOYEE"));
    }

    private String sign(JWTClaimsSet.Builder claims) throws Exception {
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner(ACCESS_KEYS.getPrivate()));
        return token.serialize();
    }
}
