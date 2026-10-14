package com.company.leave.request;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.company.leave.request.LeaveModels.*;

@Repository
public class LeaveRepository {
    private final JdbcTemplate jdbc;
    private static final String PENDING_ADJUSTMENT = "EXISTS (SELECT 1 FROM leave_adjustments a WHERE a.request_id = leave_requests.id AND a.status = 'PENDING')";
    private static final String REQUEST_SELECT = "SELECT leave_requests.*, " + PENDING_ADJUSTMENT + " AS adjustment_pending FROM leave_requests";

    public LeaveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final RowMapper<Request> ROW = (rs, index) -> new Request(
            rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class), rs.getString("requester_user_id"),
            LeaveType.valueOf(rs.getString("leave_type")), rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class), LeavePeriod.valueOf(rs.getString("leave_period")),
            rs.getInt("total_units"), rs.getString("reason"), Status.valueOf(rs.getString("status")),
            rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"), rs.getString("reviewed_by"),
            instant(rs, "reviewed_at"), rs.getString("review_note"), rs.getBoolean("adjustment_pending"));

    public void lockEmployee(UUID employeeId) {
        jdbc.update("INSERT INTO leave_request_owners(employee_id) VALUES (?) ON CONFLICT DO NOTHING", employeeId);
        jdbc.queryForObject("SELECT employee_id FROM leave_request_owners WHERE employee_id = ? FOR UPDATE", UUID.class,
                employeeId);
    }

    public boolean overlaps(UUID employeeId, LocalDate start, LocalDate end, LeavePeriod period) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM leave_requests WHERE employee_id = ?
                    AND status IN ('PENDING', 'APPROVED') AND start_date <= ? AND end_date >= ?
                    AND (start_date <> end_date OR start_date <> ? OR leave_period = 'FULL_DAY'
                         OR ? = 'FULL_DAY' OR leave_period = ?))
                """, Boolean.class, employeeId, end, start, start, period.name(), period.name()));
    }

    public Request insert(UUID employeeId, String actor, Submission body, LeavePeriod period, int totalUnits) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO leave_requests(id, employee_id, requester_user_id, leave_type, start_date, end_date,
                    leave_period, total_units, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, employeeId, actor, body.leaveType().name(), body.startDate(), body.endDate(),
                period.name(), totalUnits,
                body.reason().trim());
        return find(id).orElseThrow();
    }

    public Balance annualBalance(UUID employeeId, int year, int entitlementUnits) {
        jdbc.update("""
                INSERT INTO leave_balances(employee_id, year, entitled_units)
                VALUES (?, ?, ?) ON CONFLICT (employee_id, year) DO NOTHING
                """, employeeId, year, entitlementUnits);
        return jdbc.queryForObject("SELECT * FROM leave_balances WHERE employee_id = ? AND year = ?", (rs, i) -> {
            int entitled = rs.getInt("entitled_units");
            int used = rs.getInt("used_units");
            return new Balance(employeeId, year, entitled, used, entitled - used, days(entitled), days(used),
                    days(entitled - used));
        }, employeeId, year);
    }

    public boolean consumeAnnual(UUID employeeId, int year, int units, int entitlementUnits) {
        jdbc.update("""
                INSERT INTO leave_balances(employee_id, year, entitled_units)
                VALUES (?, ?, ?) ON CONFLICT (employee_id, year) DO NOTHING
                """, employeeId, year, entitlementUnits);
        return jdbc.update("""
                UPDATE leave_balances SET used_units = used_units + ?, updated_at = CURRENT_TIMESTAMP
                WHERE employee_id = ? AND year = ? AND used_units + ? <= entitled_units
                """, units, employeeId, year, units) == 1;
    }

    private static BigDecimal days(int units) {
        return BigDecimal.valueOf(units).divide(BigDecimal.valueOf(2));
    }

    public Optional<Request> find(UUID id) {
        return jdbc.query(REQUEST_SELECT + " WHERE id = ?", ROW, id).stream().findFirst();
    }

    public Page list(UUID employeeId, Status status, int page, int size) {
        return list(employeeId, status, page, size, false);
    }

    public Page list(UUID employeeId, Status status, int page, int size, boolean adjustmentsOnly) {
        String where = " WHERE 1=1";
        var params = new java.util.ArrayList<Object>();
        if (employeeId != null) {
            where += " AND employee_id = ?";
            params.add(employeeId);
        }
        if (status != null) {
            where += " AND status = ?";
            params.add(status.name());
        }
        if (adjustmentsOnly) where += " AND " + PENDING_ADJUSTMENT;
        long count = jdbc.queryForObject("SELECT count(*) FROM leave_requests" + where, Long.class, params.toArray());
        params.add(size);
        params.add((long) page * size);
        var content = jdbc.query(
                REQUEST_SELECT + where + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?", ROW,
                params.toArray());
        return new Page(content, page, size, count, (count + size - 1) / size);
    }

    public List<AttendanceLeave> attendance(UUID employeeId, LocalDate from, LocalDate until) {
        String filter = employeeId == null ? "" : " AND employee_id = ?";
        var params = new java.util.ArrayList<Object>();
        params.add(until); params.add(from);
        if (employeeId != null) params.add(employeeId);
        return jdbc.query("SELECT id, employee_id, leave_type, start_date, end_date, leave_period, status, version, " + PENDING_ADJUSTMENT + " AS adjustment_pending "
                + "FROM leave_requests WHERE status IN ('PENDING','APPROVED') AND start_date <= ? AND end_date >= ?"
                + filter + " ORDER BY start_date, id LIMIT 2001", (rs, row) -> new AttendanceLeave(
                    rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class),
                    LeaveType.valueOf(rs.getString("leave_type")), rs.getObject("start_date", LocalDate.class),
                    rs.getObject("end_date", LocalDate.class), LeavePeriod.valueOf(rs.getString("leave_period")),
                    Status.valueOf(rs.getString("status")), rs.getLong("version"), rs.getBoolean("adjustment_pending")), params.toArray());
    }

    public long pendingCount() {
        return jdbc.queryForObject("SELECT count(*) FROM leave_requests WHERE status = 'PENDING' OR " + PENDING_ADJUSTMENT, Long.class);
    }

    public void bumpVersion(UUID id) {
        jdbc.update("UPDATE leave_requests SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public boolean refundAnnual(UUID employee, int year, int units) {
        return jdbc.update("UPDATE leave_balances SET used_units = used_units - ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE employee_id = ? AND year = ? AND used_units >= ?", units, employee, year, units) == 1;
    }

    public void applyAdjustment(Request request, Adjustment change) {
        if (change.cancelAll()) {
            // Original approval is preserved in request history and the adjustment snapshot.
            jdbc.update("UPDATE leave_requests SET status = 'CANCELLED', reviewed_by = NULL, reviewed_at = NULL, "
                    + "review_note = NULL WHERE id = ?", request.id());
        } else {
            jdbc.update("UPDATE leave_requests SET start_date = ?, end_date = ?, leave_period = ?, total_units = ? WHERE id = ?",
                    change.startDate(), change.endDate(), change.period().name(), change.totalUnits(), request.id());
        }
    }

    public void insertAdjustment(Request request, AdjustmentSubmission body, int units, String actor) {
        jdbc.update("""
            INSERT INTO leave_adjustments(id, request_id, requester_user_id, cancel_all, original_start, original_end,
                original_period, original_units, proposed_start, proposed_end, proposed_period, proposed_units, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), request.id(), actor, body.cancelAll(), request.startDate(), request.endDate(),
                request.period().name(), request.totalUnits(), body.startDate(), body.endDate(),
                body.period() == null ? null : body.period().name(), units, body.reason().trim());
    }

    public List<Adjustment> adjustments(UUID requestId) {
        return jdbc.query("SELECT * FROM leave_adjustments WHERE request_id = ? ORDER BY created_at DESC, id", (rs, i) ->
            new Adjustment(rs.getObject("id", UUID.class), requestId, rs.getString("requester_user_id"), rs.getBoolean("cancel_all"),
                rs.getObject("original_start", LocalDate.class), rs.getObject("original_end", LocalDate.class),
                LeavePeriod.valueOf(rs.getString("original_period")), rs.getInt("original_units"),
                rs.getObject("proposed_start", LocalDate.class), rs.getObject("proposed_end", LocalDate.class),
                rs.getString("proposed_period") == null ? null : LeavePeriod.valueOf(rs.getString("proposed_period")),
                rs.getInt("proposed_units"), rs.getString("reason"), Status.valueOf(rs.getString("status")),
                instant(rs, "created_at"), rs.getString("reviewed_by"), instant(rs, "reviewed_at"), rs.getString("review_note")), requestId);
    }

    public boolean decideAdjustment(UUID id, Status target, String actor, String note) {
        return jdbc.update("UPDATE leave_adjustments SET status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, "
                + "review_note = ? WHERE id = ? AND status = 'PENDING'", target.name(), actor, note, id) == 1;
    }

    public boolean transition(UUID id, long version, Status status, String actor, String note) {
        boolean reviewed = status == Status.APPROVED || status == Status.REJECTED;
        return jdbc.update("""
                UPDATE leave_requests SET status = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP,
                    reviewed_by = ?, reviewed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END, review_note = ?
                WHERE id = ? AND version = ? AND status = 'PENDING'
                """, status.name(), reviewed ? actor : null, reviewed, note, id, version) == 1;
    }

    public void audit(UUID id, String action, String actor, String note) {
        jdbc.update("INSERT INTO leave_request_history(request_id, action, actor_user_id, note) VALUES (?, ?, ?, ?)",
                id, action, actor, note);
    }

    public List<History> history(UUID id) {
        return jdbc.query("SELECT * FROM leave_request_history WHERE request_id = ? ORDER BY id",
                (rs, i) -> new History(rs.getLong("id"), rs.getString("action"), rs.getString("actor_user_id"),
                        rs.getString("note"), instant(rs, "occurred_at")),
                id);
    }
}
