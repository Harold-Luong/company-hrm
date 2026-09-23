package com.company.employee.dto;

import com.company.employee.entity.Department;

import java.util.UUID;

public record DepartmentResponse(UUID id, String code, String name, String description) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getCode(),
                department.getName(), department.getDescription());
    }
}
