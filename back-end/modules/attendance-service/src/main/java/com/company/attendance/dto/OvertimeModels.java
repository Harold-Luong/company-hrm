package com.company.attendance.dto;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class OvertimeModels {
    private OvertimeModels() {}
    public record Write(@NotNull LocalDateTime start, @NotNull LocalDateTime end, @NotBlank @Size(max=1000) String reason) {}
    public record Response(UUID id,long version,UUID employeeId,String employeeName,LocalDateTime start,LocalDateTime end,
            String reason,String status,String reviewNote,String reviewedBy,Instant reviewedAt,Instant createdAt,
            Instant checkIn,Instant checkOut,int approvedMinutes,Integer countedMinutes) {}
    public record Summary(int approvedMinutes,int countedMinutes,List<UUID> pendingIds,List<UUID> approvedIds) {
        public static Summary empty() { return new Summary(0, 0, List.of(), List.of()); }
    }
}
