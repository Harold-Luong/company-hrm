package com.company.employee.service;

import com.company.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class EmployeeCodeGenerator {
    private final JdbcTemplate jdbc;
    private final EmployeeRepository employeeRepository;

    public String nextCode() {
        String code;
        do {
            // A database sequence allocates distinct numbers across transactions and service instances.
            Long number = jdbc.queryForObject("SELECT nextval('employee_code_seq')", Long.class);
            code = String.format(Locale.ROOT, "EMP%06d", number);
            // Existing manually assigned codes remain valid, including codes in the new format.
        } while (employeeRepository.existsByEmployeeCode(code));
        return code;
    }
}
