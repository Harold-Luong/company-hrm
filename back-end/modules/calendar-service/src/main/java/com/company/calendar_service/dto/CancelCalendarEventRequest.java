package com.company.calendar_service.dto;

import com.company.calendar_service.exception.ExceptionMessages;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelCalendarEventRequest(@NotBlank @Size(max = 1000) String reason) {
    public CancelCalendarEventRequest {
        reason = reason == null ? null : reason.strip();
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException(ExceptionMessages.UNSUPPORTED_REQUEST_FIELD.formatted(name));
    }
}
