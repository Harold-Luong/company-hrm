package com.company.attendance.dto;

import java.time.Instant;
import java.util.UUID;

public record ShiftResponse(UUID id, long version, ShiftRequest definition,
                            int requiredMinutes, boolean active, Instant updatedAt) {}
