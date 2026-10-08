package com.company.attendance.dto;
import java.time.Instant;
import java.util.*;
/** Permission explains a deviation; it never adds working minutes or consumes leave. */
public record AttendancePermissionCoverage(List<UUID> pendingRequestIds,List<UUID> approvedRequestIds,List<UUID> conflictingRequestIds,
    int approvedLateMinutes,int approvedEarlyMinutes,Long coveredLateSeconds,Long coveredEarlySeconds,
    Integer unapprovedRoundedLateMinutes,Integer unapprovedRoundedEarlyMinutes,Instant lastReviewedAt) {
    public static AttendancePermissionCoverage empty() {
        return new AttendancePermissionCoverage(List.of(),List.of(),List.of(),0,0,null,null,null,null,null);
    }
}
