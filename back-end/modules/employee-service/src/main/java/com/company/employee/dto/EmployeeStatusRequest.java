package com.company.employee.dto;

import com.company.employee.enums.EmployeeStatus;
import jakarta.validation.constraints.NotNull;

public record EmployeeStatusRequest(
        @NotNull EmployeeStatus status) {
}
