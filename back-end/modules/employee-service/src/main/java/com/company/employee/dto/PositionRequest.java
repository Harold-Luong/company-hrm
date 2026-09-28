package com.company.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PositionRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        String description
) {
    public PositionRequest {
        code = strip(code);
        name = strip(name);
        description = strip(description);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
