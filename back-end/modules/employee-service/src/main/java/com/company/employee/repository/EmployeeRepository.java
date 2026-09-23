package com.company.employee.repository;

import com.company.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    boolean existsByEmployeeCodeAndIdNot(String employeeCode, UUID id);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Override
    @EntityGraph(attributePaths = {"department", "position", "manager"})
    Page<Employee> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"department", "position", "manager"})
    Optional<Employee> findById(UUID id);
}
