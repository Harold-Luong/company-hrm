package com.company.employee.exception;

public class DuplicatePositionException extends RuntimeException {
    public DuplicatePositionException() {
        super("Position code already exists");
    }
}
