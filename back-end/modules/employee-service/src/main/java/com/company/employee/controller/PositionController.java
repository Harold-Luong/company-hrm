package com.company.employee.controller;

import com.company.employee.dto.PositionRequest;
import com.company.employee.dto.PositionResponse;
import com.company.employee.dto.PageResponse;
import com.company.employee.service.PositionService;
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
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
public class PositionController {
    private final PositionService positionService;

    @PostMapping
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionRequest request) {
        PositionResponse response = positionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/positions/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<PositionResponse> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return positionService.findAll(page, size);
    }

    @GetMapping("/{id}")
    public PositionResponse findById(@PathVariable UUID id) {
        return positionService.findById(id);
    }

    @PutMapping("/{id}")
    public PositionResponse update(@PathVariable UUID id, @Valid @RequestBody PositionRequest request) {
        return positionService.update(id, request);
    }
}
