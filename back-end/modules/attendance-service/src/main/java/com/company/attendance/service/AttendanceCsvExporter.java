package com.company.attendance.service;

import com.company.attendance.dto.AttendanceResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Human-readable daily timesheet, with an optional full reconciliation export. */
@Component
public class AttendanceCsvExporter {
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public byte[] export(List<AttendanceResponse> rows) {
        StringBuilder csv = new StringBuilder("\uFEFFMã nhân viên,Họ tên,Ngày công,Giờ vào (GMT+7),Giờ ra (GMT+7),"
                + "Công chuẩn (phút),Công ghi nhận (phút),Phép năm (phút),Nghỉ không lương (phút),"
                + "Đi trễ (phút),Về sớm (phút),OT được duyệt (phút),OT ghi nhận (phút),"
                + "Trạng thái chấm công,Cần kiểm tra,Trạng thái bảng công\r\n");
        for (var r : rows) {
            Object[] values = { r.employeeCode(), r.employeeName(), r.workDate(),
                    localTime(r.checkIn()), localTime(r.checkOut()), r.baseRequiredMinutes(), r.workMinutesCounted(),
                    r.annualLeaveMinutes(), r.unpaidLeaveMinutes(), r.roundedLateMinutes(), r.roundedEarlyMinutes(),
                    r.overtime().approvedMinutes(), r.overtime().countedMinutes(), statusLabel(r.status()),
                    reviewNotes(r), "DRAFT".equals(r.reportState()) ? "Tạm tính - chưa chốt" : "FINAL".equals(r.reportState()) ? "Đã chốt" : r.reportState() };
            csv.append(String.join(",", Arrays.stream(values).map(AttendanceCsvExporter::csvCell).toList()))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String localTime(Instant value) {
        return value == null ? "" : LOCAL_TIME.format(value);
    }

    private static String statusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "NOT_STARTED" -> "Chưa vào ca";
            case "OPEN" -> "Đang làm việc";
            case "CLOSED" -> "Đã kết thúc";
            case "NO_RECORD" -> "Chưa có ghi nhận";
            case "MISSING_CHECK_OUT" -> "Thiếu giờ ra";
            case "INVALID_RECORD" -> "Giờ vào/ra chưa hợp lệ";
            case "SOURCE_CONFLICT" -> "Cần đối soát phép và công";
            case "HOLIDAY" -> "Ngày nghỉ chung";
            case "ON_LEAVE" -> "Nghỉ phép";
            case "NO_SCHEDULE" -> "Chưa có ca";
            default -> status;
        };
    }

    private static String reviewNotes(AttendanceResponse row) {
        List<String> notes = new ArrayList<>();
        if (!row.pendingLeaveIds().isEmpty()) notes.add("Nghỉ phép chờ duyệt: " + row.pendingLeaveIds().size());
        if (!row.permissionCoverage().pendingRequestIds().isEmpty())
            notes.add("Đi trễ/về sớm chờ duyệt: " + row.permissionCoverage().pendingRequestIds().size());
        if (!row.permissionCoverage().conflictingRequestIds().isEmpty())
            notes.add("Đơn đi trễ/về sớm cần đối soát: " + row.permissionCoverage().conflictingRequestIds().size());
        if (!row.overtime().pendingIds().isEmpty()) notes.add("OT chờ duyệt: " + row.overtime().pendingIds().size());
        if (row.workMinutesCounted() == null) notes.add("Chưa xác định công");
        return String.join("; ", notes);
    }

    public byte[] exportDetailed(List<AttendanceResponse> rows) {
        StringBuilder csv = new StringBuilder(
                "\uFEFFemployee_id,employee_code,employee_name,department_id,work_date,shift_id,shift_version,check_in,check_out,required_minutes,annual_leave_minutes,unpaid_leave_minutes,leave_days,remaining_minutes,actual_seconds,counted_minutes,late_seconds,late_minutes,early_seconds,early_minutes,status,pending_leave_ids,applied_leave_ids,source_observed_at,record_version,report_state,pending_request_ids,approved_request_ids,conflicting_request_ids,approved_late_minutes,approved_early_minutes,covered_late_seconds,covered_early_seconds,unapproved_rounded_late_minutes,unapproved_rounded_early_minutes,approved_ot_minutes,counted_ot_minutes,original_check_in,original_check_out,correction_id\r\n");
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
                    r.overtime().countedMinutes(), r.originalCheckIn(), r.originalCheckOut(), r.correctionId() };
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
