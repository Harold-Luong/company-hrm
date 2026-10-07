package com.company.leave.request;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class LeaveModels {
    private LeaveModels() {
    }

    public enum LeaveType {
        ANNUAL, UNPAID
    }

    public enum Status {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    public enum LeavePeriod {
        FULL_DAY, MORNING, AFTERNOON
    }

    public record Submission(@NotNull LeaveType leaveType, @NotNull LocalDate startDate,
            @NotNull LocalDate endDate, LeavePeriod period, @NotBlank @Size(max = 2000) String reason) {
    }

    public record Decision(@Size(max = 2000) String note) {
    }

    public record Request(UUID id, UUID employeeId, String requesterUserId, LeaveType leaveType,
            LocalDate startDate, LocalDate endDate, LeavePeriod period, int totalUnits, String reason, Status status, long version,
            Instant createdAt, Instant updatedAt, String reviewedBy, Instant reviewedAt, String reviewNote) {
    }

    public record Balance(UUID employeeId, int year, int entitledUnits, int usedUnits, int remainingUnits,
            BigDecimal entitledDays, BigDecimal usedDays, BigDecimal remainingDays) {
    }

    public record PendingCount(long count) {
    }

    /** Minimal attendance projection; excludes reasons and review notes. */
    public record AttendanceLeave(UUID id, UUID employeeId, LeaveType leaveType, LocalDate startDate,
            LocalDate endDate, LeavePeriod period, Status status, long version) {}

    public record History(long id, String action, String actorUserId, String note, Instant occurredAt) {
    }

    public record Page(List<Request> content, int page, int size, long totalElements, long totalPages) {
    }
}
