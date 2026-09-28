package com.example.authservice.provisioning;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record AccountProvisioningResult(
        @NotNull UUID eventId, @NotBlank String eventType, int schemaVersion,
        @NotNull Instant occurredAt, @NotBlank String producer,
        @NotNull UUID correlationId, @NotNull UUID requestId, @NotNull @Valid Data data) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(@NotNull UUID employeeId, String accountStatus, Long accountVersion, String errorCode) {}

    public static AccountProvisioningResult of(EmployeeAccountRequested request, String errorCode, Long version) {
        return new AccountProvisioningResult(UUID.randomUUID(),
                errorCode == null ? "AccountCreated" : "AccountCreationFailed", 1,
                Instant.now(), "auth-service", request.correlationId(), request.requestId(),
                new Data(request.data().employeeId(), errorCode == null ? "PENDING_ACTIVATION" : null,
                        version, errorCode));
    }
}
