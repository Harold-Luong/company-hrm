package com.company.calendar_service;

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
import com.company.calendar_service.calendar.CalendarController;
import com.company.calendar_service.calendar.CalendarManagementController;
import com.company.calendar_service.calendar.CalendarManagementService;
import com.company.calendar_service.calendar.CalendarService;
import com.company.calendar_service.config.SecurityConfig;
import com.company.calendar_service.dto.CalendarResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CalendarController.class, CalendarManagementController.class})
@Import(SecurityConfig.class)
class JwtSecurityTests extends JwtTestSupport {
    private static final String EMPLOYEE_ID = "d38e31b7-0bba-420c-8eaf-50edbe5a61ae";

    @Autowired private MockMvc mvc;
    @MockitoBean private CalendarService calendarService;
    @MockitoBean private CalendarManagementService managementService;

    @BeforeEach
    void calendarResponse() {
        when(calendarService.read(anyInt())).thenAnswer(invocation ->
                new CalendarResponse(invocation.getArgument(0), List.of(2026), List.of()));
    }
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtAuthenticationConverter converter;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/calendar"})
    void requiresTokenOnAllBusinessApis(String path) throws Exception {
        for (var method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            mvc.perform(request(method, path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.status").value(401));
        }
        mvc.perform(get(path + "/" + EMPLOYEE_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAuthAccessTokenFormatAndDoesNotCreateSession() throws Exception {
        String token = sign(claims());
        for (String path : List.of("/api/v1/calendar")) {
            var result = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
        }
        var authentication = converter.convert(decoder.decode(token));
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_HR", "ROLE_EMPLOYEE");
        assertThat(decoder.decode(token).getClaimAsString("employee_id")).isEqualTo(EMPLOYEE_ID);
        mvc.perform(get("/api/v1/calendar")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER", "HR", "ADMIN"})
    void everyAuthenticatedEmployeeRoleCanReadButCannotWrite(String role) throws Exception {
        String token = sign(claims().claim("roles", List.of(role)));
        mvc.perform(get("/api/v1/calendar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        for (var method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            mvc.perform(request(method, "/api/v1/calendar")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
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
        mvc.perform(get("/api/v1/calendar").param("access_token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesOtherResourcesEvenWithValidToken() throws Exception {
        mvc.perform(get("/docs/sql/001_employee_schema.sql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
    void managementEndpointsRejectNonManagersAndAnonymousCallers(String role) throws Exception {
        String token = sign(claims().claim("roles", List.of(role)));
        String[] paths = {"", "/1", "", "/1", "/1", "/1/publish", "/1/cancel"};
        HttpMethod[] methods = {HttpMethod.GET, HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.PATCH};
        for (int i = 0; i < paths.length; i++) {
            mvc.perform(request(methods[i], "/api/v1/calendar-events" + paths[i]))
                    .andExpect(status().isUnauthorized());
            mvc.perform(request(methods[i], "/api/v1/calendar-events" + paths[i])
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HR", "ADMIN"})
    void authRolesAllowManagementReads(String role) throws Exception {
        mvc.perform(get("/api/v1/calendar-events").param("from", "2090-01-01").param("to", "2090-01-31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims().claim("roles", List.of(role)))))
                .andExpect(status().isOk());
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/v1/calendar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
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
