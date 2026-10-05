package com.company.leave;

import com.company.leave.config.SecurityConfig;
import com.company.leave.request.LeaveController;
import com.company.leave.request.LeaveModels.Page;
import com.company.leave.request.LeaveService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaveController.class)
@Import(SecurityConfig.class)
class LeaveSecurityTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @MockitoBean LeaveService service;
    static final String BASE = "/api/v1/leave/requests";

    private JWTClaimsSet.Builder claims(String role) {
        return new JWTClaimsSet.Builder().subject("employee-user").issuer("auth-service").audience("hrm-api-access")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("employee_id", "d38e31b7-0bba-420c-8eaf-50edbe5a61ae").claim("roles", List.of(role));
    }
    private String sign(JWTClaimsSet.Builder claims) throws Exception {
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner(ACCESS_KEYS.getPrivate()));
        return "Bearer " + token.serialize();
    }
    @Test
    void anonymousCannotReadOrSubmit() throws Exception {
        mvc.perform(get(BASE + "/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @ParameterizedTest
    @ValueSource(strings = {"SICK", "OTHER"})
    void removedLeaveTypesAreRejectedBeforeCallingService(String leaveType) throws Exception {
        mvc.perform(post(BASE).header("Authorization", sign(claims("EMPLOYEE")))
                .contentType("application/json").content("""
                    {"leaveType":"%s","startDate":"2030-02-01","endDate":"2030-02-01",
                     "period":"FULL_DAY","reason":"Personal matter"}
                    """.formatted(leaveType)))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }
    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER", "HR", "ADMIN"})
    void validAuthTokenCanReadOwnRequests(String role) throws Exception {
        when(service.list(eq(false), any(), anyInt(), anyInt(), any())).thenReturn(new Page(List.of(), 0, 20, 0, 0));
        mvc.perform(get(BASE + "/mine").header("Authorization", sign(claims(role))))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
    void nonHrCannotReadInboxOrReview(String role) throws Exception {
        String token = sign(claims(role));
        mvc.perform(get(BASE + "/inbox").header("Authorization", token)).andExpect(status().isForbidden());
        for (String action : List.of("approve", "reject")) {
            mvc.perform(patch(BASE + "/d38e31b7-0bba-420c-8eaf-50edbe5a61ae/" + action)
                    .header("Authorization", token).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"HR", "ADMIN"})
    void reviewersCanReadInbox(String role) throws Exception {
        when(service.list(eq(true), any(), anyInt(), anyInt(), any())).thenReturn(new Page(List.of(), 0, 20, 0, 0));
        mvc.perform(get(BASE + "/inbox").header("Authorization", sign(claims(role))))
                .andExpect(status().isOk());
    }
    @Test
    void employeeCannotReadPendingCount() throws Exception {
        mvc.perform(get(BASE + "/pending-count").header("Authorization", sign(claims("EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "employee", "roles", "subject", "refresh", "noExpiry"})
    void invalidSignedTokensAreRejected(String invalid) throws Exception {
        var claims = claims("HR");
        switch (invalid) {
            case "issuer" -> claims.issuer("other");
            case "audience" -> claims.audience("hrm-api-refresh");
            case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(30)));
            case "employee" -> claims.claim("employee_id", "not-a-uuid");
            case "roles" -> claims.claim("roles", "HR");
            case "subject" -> claims.subject(null);
            case "refresh" -> claims.claim("type", "refresh");
            case "noExpiry" -> claims.expirationTime(null);
        }
        mvc.perform(get(BASE + "/mine").header("Authorization", sign(claims))).andExpect(status().isUnauthorized());
    }
}
