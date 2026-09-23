package com.company.employee.service;

import com.company.employee.dto.EmployeeRequest;
import com.company.employee.dto.EmployeeResponse;
import com.company.employee.dto.PageResponse;
import com.company.employee.entity.Employee;
import com.company.employee.enums.EmployeeStatus;
import com.company.employee.exception.DuplicateEmployeeException;
import com.company.employee.exception.InvalidEmployeeException;
import com.company.employee.exception.ResourceNotFoundException;
import com.company.employee.repository.DepartmentRepository;
import com.company.employee.repository.EmployeeRepository;
import com.company.employee.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        validateUniqueFields(request, null);
        Employee employee = new Employee();
        applyRequest(employee, request);
        employeeRepository.save(employee);
        return EmployeeResponse.from(employee);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> findAll(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("employeeCode").ascending());
        return PageResponse.from(employeeRepository.findAll(pageable).map(EmployeeResponse::from));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(UUID id) {
        return EmployeeResponse.from(requireEmployee(id));
    }

    @Transactional
    public EmployeeResponse update(UUID id, EmployeeRequest request) {
        Employee employee = requireEmployee(id);
        validateUniqueFields(request, id);
        applyRequest(employee, request);

        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse updateStatus(UUID id, EmployeeStatus status) {
        Employee employee = requireEmployee(id);
        employee.setStatus(status);
        return EmployeeResponse.from(employee);
    }

    private Employee requireEmployee(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private void validateUniqueFields(EmployeeRequest request, UUID id) {
        boolean duplicateCode = id == null
                ? employeeRepository.existsByEmployeeCode(request.employeeCode())
                : employeeRepository.existsByEmployeeCodeAndIdNot(request.employeeCode(), id);
        if (duplicateCode) {
            throw new DuplicateEmployeeException("employeeCode");
        }
        boolean duplicateEmail = id == null
                ? employeeRepository.existsByEmail(request.email())
                : employeeRepository.existsByEmailAndIdNot(request.email(), id);
        if (duplicateEmail) {
            throw new DuplicateEmployeeException("email");
        }
    }

    private void applyRequest(Employee employee, EmployeeRequest request) {
        if (request.dateOfBirth() != null && !request.dateOfBirth().isBefore(request.hireDate())) {
            throw new InvalidEmployeeException("dateOfBirth must be before hireDate");
        }

        var department = request.departmentId() == null ? null
                : departmentRepository.findById(request.departmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Department", request.departmentId()));
        var position = request.positionId() == null ? null
                : positionRepository.findById(request.positionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Position", request.positionId()));
        Employee manager = resolveManager(employee.getId(), request.managerId());

        employee.setEmployeeCode(request.employeeCode());
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setHireDate(request.hireDate());
        employee.setStatus(request.status());
        employee.setDepartment(department);
        employee.setPosition(position);
        employee.setManager(manager);
    }

    private Employee resolveManager(UUID employeeId, UUID managerId) {
        if (managerId == null) {
            return null;
        }
        Employee manager = requireEmployee(managerId);
        Set<UUID> visited = new HashSet<>();
        for (Employee current = manager; current != null; current = current.getManager()) {
            if (current.getId().equals(employeeId) || !visited.add(current.getId())) {
                throw new InvalidEmployeeException("managerId must not create a cycle in the manager hierarchy");
            }
        }
        return manager;
    }
}
