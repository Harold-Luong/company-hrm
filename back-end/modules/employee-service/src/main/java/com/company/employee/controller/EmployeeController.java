package com.company.employee.controller;

import com.company.employee.dto.EmployeeRequest;
import com.company.employee.dto.EmployeeResponse;
import com.company.employee.dto.EmployeeStatusRequest;
import com.company.employee.dto.PageResponse;
import com.company.employee.service.EmployeeService;
import com.company.employee.service.HealthCheckService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService employeeService;
    private final HealthCheckService healthCheckService;

    @GetMapping("/health-check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        boolean connected = healthCheckService.isDatabaseConnected();
        return ResponseEntity.status(connected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", connected ? "UP" : "DOWN",
                        "database", connected ? "UP" : "DOWN"));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse response = employeeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<EmployeeResponse> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return employeeService.findAll(page, size);
    }

    @GetMapping("/{id}")
    public EmployeeResponse findById(@PathVariable UUID id) {
        return employeeService.findById(id);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public EmployeeResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody EmployeeStatusRequest request) {
        return employeeService.updateStatus(id, request.status());
    }
}
