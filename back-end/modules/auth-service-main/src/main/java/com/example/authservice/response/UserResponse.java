package com.example.authservice.response;

import com.example.authservice.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Set;

@JsonPropertyOrder({ "id", "employeeId", "email", "active", "roles", "lastLoginAt", "createdAt", "updatedAt" })
public record UserResponse(
        Long id,
        Long employeeId,
        String email,
        boolean active,
        Set<UserRole> roles,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {
}
