package com.company.employee.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.UUID;

/** Validates the access-token contract emitted by Auth, before authorities are read. */
public class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object subject = jwt.getClaims().get("sub");
        Object employeeId = jwt.getClaims().get("employee_id");
        Object roles = jwt.getClaims().get("roles");
        Object type = jwt.getClaims().get("type");
        if (jwt.getExpiresAt() == null
                || !(subject instanceof String value) || value.isBlank()
                || !isEmployeeId(employeeId)
                || !(roles instanceof Collection<?> values) || values.isEmpty()
                || values.stream().anyMatch(role -> !(role instanceof String name) || name.isBlank())
                || (type != null && !"access".equals(type))) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid access token claims", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean isEmployeeId(Object value) {
        if (!(value instanceof String id)) {
            return false;
        }
        try {
            return UUID.fromString(id).toString().equalsIgnoreCase(id);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
