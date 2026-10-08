package com.company.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Chỉ giữ dữ liệu cần đối soát ngày công, không sao chép toàn bộ hồ sơ hoặc lịch cả kỳ. */
@Component
@RequiredArgsConstructor
public class AttendanceSnapshots {
    private final ObjectMapper mapper;

    public String employee(WorkforceClient.Employee person) {
        return mapper.writeValueAsString(new EmployeeSnapshot(person.id(), person.employeeCode(),
                person.firstName(), person.lastName(),
                person.department() == null ? null : new DepartmentSnapshot(person.department().id())));
    }

    public String leaves(UUID employee, LocalDate date, List<WorkforceClient.Leave> rows) {
        return mapper.writeValueAsString(rows.stream()
                .filter(row -> employee.equals(row.employeeId()))
                .filter(row -> "PENDING".equals(row.status()) || "APPROVED".equals(row.status()))
                .filter(row -> !row.startDate().isAfter(date) && !row.endDate().isBefore(date)).toList());
    }

    public String holidays(LocalDate date, List<WorkforceClient.Holiday> rows) {
        return mapper.writeValueAsString(rows.stream()
                .filter(row -> !row.startDate().isAfter(date) && !row.endDate().isBefore(date)).toList());
    }

    private record DepartmentSnapshot(UUID id) {}
    private record EmployeeSnapshot(UUID id, String employeeCode, String firstName,
                                    String lastName, DepartmentSnapshot department) {}
}
