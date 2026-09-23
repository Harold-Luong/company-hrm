package com.company.employee.dto;

import com.company.employee.entity.Employee;
import com.company.employee.enums.AccountStatus;
import com.company.employee.enums.EmployeeStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        AccountStatus accountStatus,
        String phone,
        LocalDate dateOfBirth,
        LocalDate hireDate,
        EmployeeStatus status,
        Reference department,
        Reference position,
        Manager manager,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Reference(UUID id, String code, String name) {
    }

    public record Manager(UUID id, String employeeCode, String firstName, String lastName) {
    }

    public static EmployeeResponse from(Employee employee) {
        var department = employee.getDepartment();
        var position = employee.getPosition();
        var manager = employee.getManager();
        return new EmployeeResponse(
                employee.getId(), employee.getEmployeeCode(), employee.getFirstName(),
                employee.getLastName(), employee.getEmail(), employee.getAccountStatus(), employee.getPhone(),
                employee.getDateOfBirth(), employee.getHireDate(), employee.getStatus(),
                department == null ? null : new Reference(department.getId(), department.getCode(), department.getName()),
                position == null ? null : new Reference(position.getId(), position.getCode(), position.getName()),
                manager == null ? null : new Manager(manager.getId(), manager.getEmployeeCode(), manager.getFirstName(), manager.getLastName()),
                employee.getCreatedAt(), employee.getUpdatedAt());
    }
}
