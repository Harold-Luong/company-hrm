package com.company.employee.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, UUID id) {
        super(ExceptionMessages.RESOURCE_NOT_FOUND.formatted(resource, id));
    }
}
