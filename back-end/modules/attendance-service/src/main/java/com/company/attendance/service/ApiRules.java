package com.company.attendance.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

public final class ApiRules {
    private ApiRules() {
    }

    public static ResponseStatusException error(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }

    public static UUID employee(JwtAuthenticationToken actor) {
        return UUID.fromString(actor.getToken().getClaimAsString("employee_id"));
    }

    public static boolean reviewer(JwtAuthenticationToken actor) {
        return actor.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    public static void requireReviewer(JwtAuthenticationToken actor) {
        if (!reviewer(actor))
            throw error(HttpStatus.FORBIDDEN, "HR or ADMIN role is required");
    }

    public static void requireVersion(String match, long version) {
        if (match == null)
            throw error(HttpStatus.PRECONDITION_REQUIRED, "If-Match is required");
        if (!match.equals("\"" + version + "\""))
            throw error(HttpStatus.PRECONDITION_FAILED, "Resource changed; reload before retrying");
    }

    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
    }
}
