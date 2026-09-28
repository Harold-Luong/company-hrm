package com.company.employee.provisioning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record EmployeeAccountRequested(
        @NotNull UUID eventId, @NotBlank String eventType, int schemaVersion,
        @NotNull Instant occurredAt, @NotBlank String producer,
        @NotNull UUID correlationId, @NotNull UUID requestId, @NotNull @Valid Data data) {
    public record Data(@NotNull UUID employeeId, @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String requestedBy) {
    }

    public static EmployeeAccountRequested create(UUID requestId, UUID employeeId, String email, String requestedBy) {
        return new EmployeeAccountRequested(UUID.randomUUID(), "EmployeeAccountRequested", 1,
                Instant.now(), "employee-service", UUID.randomUUID(), requestId,
                new Data(employeeId, email, requestedBy));
    }
}
