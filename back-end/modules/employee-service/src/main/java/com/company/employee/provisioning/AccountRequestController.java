package com.company.employee.provisioning;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/employees/{employeeId}/account-requests")
public class AccountRequestController {
    private final AccountRequestService service;

    @PostMapping
    public ResponseEntity<AccountRequestService.Response> request(@PathVariable UUID employeeId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody AccountRequestService.Request request) {
        var result = service.request(employeeId, idempotencyKey, request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/employees/" + employeeId
                + "/account-requests/" + result.requestId())).body(result);
    }

    @GetMapping("/{requestId}")
    public AccountRequestService.Response find(@PathVariable UUID employeeId, @PathVariable UUID requestId) {
        return service.find(employeeId, requestId);
    }
}
