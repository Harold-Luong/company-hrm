package com.company.attendance.controller;

import com.company.attendance.dto.HealthCheckResponse;
import com.company.attendance.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class HealthCheckController {
    private final HealthCheckService healthCheckService;

    @GetMapping("/health-check")
    public ResponseEntity<HealthCheckResponse> healthCheck() {
        boolean connected = healthCheckService.isDatabaseConnected();
        String status = connected ? "UP" : "DOWN";
        return ResponseEntity.status(connected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthCheckResponse(status, status));
    }
}
