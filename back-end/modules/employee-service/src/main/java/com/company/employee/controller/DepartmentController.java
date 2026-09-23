package com.company.employee.controller;

import com.company.employee.dto.DepartmentRequest;
import com.company.employee.dto.DepartmentResponse;
import com.company.employee.dto.PageResponse;
import com.company.employee.service.DepartmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse response = departmentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<DepartmentResponse> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return departmentService.findAll(page, size);
    }

    @GetMapping("/{id}")
    public DepartmentResponse findById(@PathVariable UUID id) {
        return departmentService.findById(id);
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return departmentService.update(id, request);
    }
}
