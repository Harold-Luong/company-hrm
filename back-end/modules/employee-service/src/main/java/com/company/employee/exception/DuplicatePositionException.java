package com.company.employee.exception;

public class DuplicatePositionException extends RuntimeException {
    public DuplicatePositionException() {
        super(ExceptionMessages.DUPLICATE_POSITION_CODE);
    }
}
