package com.company.employee.exception;

public class DuplicateEmployeeException extends RuntimeException {
    public DuplicateEmployeeException(String field) {
        super("Employee with this " + field + " already exists");
    }
}
