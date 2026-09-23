package com.company.employee.dto;

import com.company.employee.enums.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRequest(
        @NotBlank @Size(max = 50) String employeeCode,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Past LocalDate dateOfBirth,
        @NotNull LocalDate hireDate,
        @NotNull EmployeeStatus status,
        UUID departmentId,
        UUID positionId,
        UUID managerId
) {
    public EmployeeRequest {
        employeeCode = strip(employeeCode);
        firstName = strip(firstName);
        lastName = strip(lastName);
        email = strip(email);
        phone = strip(phone);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
