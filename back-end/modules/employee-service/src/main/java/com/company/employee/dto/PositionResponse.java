package com.company.employee.dto;

import com.company.employee.entity.Position;

import java.util.UUID;

public record PositionResponse(UUID id, String code, String name, String description) {
    public static PositionResponse from(Position position) {
        return new PositionResponse(position.getId(), position.getCode(),
                position.getName(), position.getDescription());
    }
}
