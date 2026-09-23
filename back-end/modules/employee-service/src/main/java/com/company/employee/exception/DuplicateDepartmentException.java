package com.company.employee.exception;

public class DuplicateDepartmentException extends RuntimeException {
    public DuplicateDepartmentException() {
        super("Department code already exists");
    }
}
