package com.company.attendance.service;

import com.company.attendance.dto.AttendancePermissionCoverage;
import com.company.attendance.dto.AttendanceResponse;
import com.company.attendance.dto.OvertimeModels;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceCsvExporterTests {
    private final AttendanceCsvExporter exporter = new AttendanceCsvExporter();
    private final UUID id = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private AttendanceResponse row(Integer counted, String status) {
        return new AttendanceResponse(id, "=EMP001", "Nguyễn, \"An\"", id, LocalDate.of(2030, 1, 7), id, 3L,
                Instant.parse("2030-01-06T18:00:00Z"), counted == null ? null : Instant.parse("2030-01-07T02:00:00Z"),
                480, 120, 60, new BigDecimal("0.5"), 300, counted == null ? null : 16800L, counted,
                counted == null ? null : 480L, counted == null ? null : 15,
                counted == null ? null : 0L, counted == null ? null : 0,
                status, List.of(id), List.of(id), Instant.parse("2030-01-07T02:00:01Z"), 8L, "DRAFT",
                new AttendancePermissionCoverage(List.of(id), List.of(id), List.of(id), 8, 0,
                        480L, 0L, 0, 0, null),
                new OvertimeModels.Summary(120, 110, List.of(id), List.of(id)),
                Instant.parse("2030-01-06T17:59:00Z"), null, id);
    }

    @Test
    void compactExportKeepsPayrollInputsWithoutInternalIdsOrDuplicateMetrics() {
        String csv = new String(exporter.export(List.of(row(270, "CLOSED"))), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFMã nhân viên,Họ tên,Ngày công");
        assertThat(csv.split("\r\n")[0].split(",")).hasSize(16);
        assertThat(csv).contains("\"'=EMP001\",\"Nguyễn, \"\"An\"\"\",\"2030-01-07\","
                + "\"2030-01-07 01:00:00\",\"2030-01-07 09:00:00\","
                + "\"480\",\"270\",\"120\",\"60\",\"15\",\"0\",\"120\",\"110\",\"Đã kết thúc\"");
        assertThat(csv).contains("Nghỉ phép chờ duyệt: 1", "Đi trễ/về sớm chờ duyệt: 1",
                "Đơn đi trễ/về sớm cần đối soát: 1", "OT chờ duyệt: 1", "Tạm tính - chưa chốt");
        assertThat(csv).doesNotContain(id.toString(), "employee_id", "record_version", "actual_seconds",
                "2030-01-06T17:59:00Z");
    }

    @Test
    void unknownWorkIsBlankRatherThanZeroAndTheIssueStaysVisible() {
        String csv = new String(exporter.export(List.of(row(null, "MISSING_CHECK_OUT"))), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"2030-01-07 01:00:00\",\"\",\"480\",\"\",\"120\",\"60\",\"\",\"\"");
        assertThat(csv).contains("Thiếu giờ ra", "Chưa xác định công", "Tạm tính - chưa chốt");
    }

    @Test
    void detailedExportPreservesReconciliationFields() {
        String csv = new String(exporter.exportDetailed(List.of(row(270, "CLOSED"))), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFemployee_id,employee_code").contains(id.toString(), "record_version",
                "approved_request_ids", "original_check_in,original_check_out,correction_id", "\"DRAFT\"");
        assertThat(csv.split("\r\n")[0].split(",")).hasSize(40);
    }

    @Test
    void emptyReportStillExportsTheCompactHeader() {
        String csv = new String(exporter.export(List.of()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFMã nhân viên").endsWith("Trạng thái bảng công\r\n");
        assertThat(csv.split("\r\n")).hasSize(1);
    }

    @Test
    void spreadsheetFormulasAndQuotesAreEscaped() {
        for (String value : List.of("=1+1", " +1", "-1", "@SUM(A1)", "\tformula", "\rformula", "\nformula")) {
            assertThat(AttendanceCsvExporter.csvCell(value)).startsWith("\"'");
        }
        assertThat(AttendanceCsvExporter.csvCell("An, \"Bình\"")).isEqualTo("\"An, \"\"Bình\"\"\"");
    }
}
