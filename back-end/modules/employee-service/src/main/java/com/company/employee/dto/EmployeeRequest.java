package com.company.employee.dto;

import com.company.employee.enums.EmployeeStatus;
import com.company.employee.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String personalEmail,
        @Size(max = 30) String phone,
        @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @Size(max = 255) String address,
        @Size(max = 255) String contactRelative,
        @Size(max = 30) String contactRelativePhone,
        @NotNull LocalDate hireDate,
        @NotNull EmployeeStatus status,
        UUID departmentId,
        UUID positionId,
        UUID managerId
) {
    public EmployeeRequest {
        firstName = strip(firstName);
        lastName = strip(lastName);
        personalEmail = strip(personalEmail);
        address = strip(address);
        contactRelative = strip(contactRelative);
        contactRelativePhone = strip(contactRelativePhone);
        phone = strip(phone);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
