package com.company.employee.exception;

public class DuplicateDepartmentException extends RuntimeException {
    public DuplicateDepartmentException() {
        super(ExceptionMessages.DUPLICATE_DEPARTMENT_CODE);
    }
}
