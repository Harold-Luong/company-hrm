package com.company.employee.service;

import com.company.employee.dto.DepartmentRequest;
import com.company.employee.dto.DepartmentResponse;
import com.company.employee.dto.PageResponse;
import com.company.employee.entity.Department;
import com.company.employee.exception.DuplicateDepartmentException;
import com.company.employee.exception.ResourceNotFoundException;
import com.company.employee.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        if (departmentRepository.existsByCode(request.code())) {
            throw new DuplicateDepartmentException();
        }
        Department department = new Department();
        department.setName(request.name());
        department.setCode(request.code());
        department.setDescription(request.description());
        departmentRepository.save(department);
        return DepartmentResponse.from(department);
    }

    public PageResponse<DepartmentResponse> findAll(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("code").ascending());
        return PageResponse.from(departmentRepository.findAll(pageable).map(DepartmentResponse::from));
    }

    public DepartmentResponse findById(UUID id) {
        return departmentRepository.findById(id).map(DepartmentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    @Transactional
    public DepartmentResponse update(UUID id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
        if (departmentRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new DuplicateDepartmentException();
        }
        department.setCode(request.code());
        department.setName(request.name());
        department.setDescription(request.description());
        return DepartmentResponse.from(department);
    }
}
