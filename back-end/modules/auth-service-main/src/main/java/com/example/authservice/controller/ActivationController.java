package com.example.authservice.controller;

import com.example.authservice.activation.ActivationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class ActivationController {
    private final ActivationService activation;
    public record ActivateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "ActivateRequest[REDACTED]"; }
    }
    @PostMapping("/activate")
    public Map<String, String> activate(@Valid @RequestBody ActivateRequest request) {
        activation.activate(request.token(), request.password());
        return Map.of("message", "Account activated successfully");
    }
    @GetMapping("/activation-invitations/{employeeId}")
    public ActivationService.DeliveryStatus latest(@PathVariable UUID employeeId) {
        return activation.latest(employeeId);
    }
    @PostMapping("/activation-invitations/{employeeId}")
    public ResponseEntity<ActivationService.Invitation> resend(@PathVariable UUID employeeId) {
        return ResponseEntity.accepted().body(activation.resend(employeeId));
    }
}
