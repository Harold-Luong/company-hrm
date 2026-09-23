package com.company.employee.repository;

import com.company.employee.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
