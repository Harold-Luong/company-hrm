package com.company.attendance.service;

import com.company.attendance.dto.AttendanceResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** CSV representation of the same report returned by the JSON API. */
@Component
public class AttendanceCsvExporter {
    public byte[] export(List<AttendanceResponse> rows) {
        StringBuilder csv = new StringBuilder(
                "\uFEFFemployee_id,employee_code,employee_name,department_id,work_date,shift_id,shift_version,check_in,check_out,required_minutes,annual_leave_minutes,unpaid_leave_minutes,leave_days,remaining_minutes,actual_seconds,counted_minutes,late_seconds,late_minutes,early_seconds,early_minutes,status,pending_leave_ids,applied_leave_ids,source_observed_at,record_version,report_state,pending_request_ids,approved_request_ids,conflicting_request_ids,approved_late_minutes,approved_early_minutes,covered_late_seconds,covered_early_seconds,unapproved_rounded_late_minutes,unapproved_rounded_early_minutes,approved_ot_minutes,counted_ot_minutes\r\n");
        for (var r : rows) {
            Object[] values = { r.employeeId(), r.employeeCode(), r.employeeName(), r.departmentId(), r.workDate(),
                    r.shiftId(), r.shiftVersion(),
                    r.checkIn(), r.checkOut(), r.baseRequiredMinutes(), r.annualLeaveMinutes(), r.unpaidLeaveMinutes(),
                    r.leaveDays(),
                    r.remainingRequiredMinutes(), r.workedActualSeconds(), r.workMinutesCounted(),
                    r.lateActualSeconds(), r.roundedLateMinutes(),
                    r.earlyActualSeconds(), r.roundedEarlyMinutes(), r.status(), r.pendingLeaveIds(),
                    r.appliedLeaveIds(),
                    r.sourceObservedAt(), r.recordVersion(), r.reportState(),
                    r.permissionCoverage().pendingRequestIds(), r.permissionCoverage().approvedRequestIds(),
                    r.permissionCoverage().conflictingRequestIds(),
                    r.permissionCoverage().approvedLateMinutes(), r.permissionCoverage().approvedEarlyMinutes(),
                    r.permissionCoverage().coveredLateSeconds(), r.permissionCoverage().coveredEarlySeconds(),
                    r.permissionCoverage().unapprovedRoundedLateMinutes(),
                    r.permissionCoverage().unapprovedRoundedEarlyMinutes(), r.overtime().approvedMinutes(),
                    r.overtime().countedMinutes() };
            csv.append(String.join(",", Arrays.stream(values).map(AttendanceCsvExporter::csvCell).toList()))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0
                || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n"))
            text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
