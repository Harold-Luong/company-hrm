package com.company.employee.exception;

public class DuplicateEmployeeException extends RuntimeException {
    public DuplicateEmployeeException(String field) {
        super(ExceptionMessages.DUPLICATE_EMPLOYEE.formatted(field));
    }
}
