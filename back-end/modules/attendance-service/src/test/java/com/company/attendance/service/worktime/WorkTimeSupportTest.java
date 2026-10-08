package com.company.attendance.service.worktime;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.company.attendance.service.worktime.WorkTimeInput.*;
import static com.company.attendance.service.worktime.WorkTimeResult.*;

class WorkTimeSupportTest {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 7);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private Instant at(String time) { return at(DATE, time); }
    private Instant at(LocalDate date, String time) { return date.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant(); }
    private List<WorkInterval> schedule(LocalDate date) {
        return List.of(new WorkInterval("MORNING", at(date, "08:00"), at(date, "12:00")),
                new WorkInterval("AFTERNOON", at(date, "13:30"), at(date, "17:30")));
    }
    private WorkTimeInput input(CalendarDay holiday, List<Leave> leaves, Punch punch, List<Overtime> ot) {
        return new WorkTimeInput(DATE, ZONE, schedule(DATE), holiday, leaves, punch, ot, at("22:00"), at("23:00"), WorkTimePolicy.standard());
    }
    private Leave leave(String period, LeaveType type) { return new Leave("leave", period, type, Approval.APPROVED); }
    private WorkTimeResult calculate(List<Leave> leaves, Punch punch) {
        return WorkTimeSupport.calculate(input(CalendarDay.WORKDAY, leaves, punch, List.of()));
    }

    @Test
    void fullShiftExcludesLunchAndCountsOnePaidDay() {
        var result = calculate(List.of(), new Punch(at("07:30"), at("19:00")));
        assertEquals(Status.CLOSED, result.status());
        assertEquals(480 * 60L, result.workedActualSeconds());
        assertEquals(480, result.workedCountedMinutes());
        assertEquals(480, result.payableMinutes());
        assertEquals(new BigDecimal("1.000000"), result.payableDays());
        assertEquals(0, result.countedOvertimeMinutes());
    }

    @Test
    void lateAndEarlyAreRoundedSeparatelyAndDoNotUseExtraHours() {
        var result = calculate(List.of(), new Punch(at("08:08"), at("17:25")));
        assertEquals(467 * 60L, result.workedActualSeconds());
        assertEquals(15, result.roundedLateMinutes());
        assertEquals(15, result.roundedEarlyMinutes());
        assertEquals(450, result.payableMinutes());
        assertEquals(new BigDecimal("0.937500"), result.payableDays());
        assertEquals(465, calculate(List.of(), new Punch(at("08:08"), at("19:00"))).payableMinutes());
    }

    @Test
    void paidHolidayNeedsNoPunchAndDoesNotChargeOverlappingLeave() {
        var result = WorkTimeSupport.calculate(input(CalendarDay.PAID_HOLIDAY,
                List.of(leave(FULL_DAY, LeaveType.ANNUAL_PAID)), Punch.none(), List.of()));
        assertEquals(Status.HOLIDAY, result.status());
        assertEquals(480, result.scheduledMinutes());
        assertEquals(0, result.requiredWorkMinutes());
        assertEquals(480, result.paidHolidayMinutes());
        assertEquals(0, result.annualLeaveMinutes());
        assertTrue(result.appliedLeaveIds().isEmpty());
        assertEquals(0, result.workedCountedMinutes());
        assertEquals(480, result.payableMinutes());
    }

    @Test
    void approvedAnnualLeaveIsPaidWithoutInventingWorkedTime() {
        var result = calculate(List.of(leave(FULL_DAY, LeaveType.ANNUAL_PAID)), Punch.none());
        assertEquals(Status.ON_LEAVE, result.status());
        assertEquals(0L, result.workedActualSeconds());
        assertEquals(480, result.annualLeaveMinutes());
        assertEquals(480, result.payableMinutes());
        assertEquals(new BigDecimal("1.000000"), result.paidAbsenceDays());
        assertEquals(List.of("leave"), result.appliedLeaveIds());
    }

    @Test
    void halfDayPaidLeaveAndHalfDayWorkMakeAFullPaidDay() {
        var result = calculate(List.of(leave("MORNING", LeaveType.ANNUAL_PAID)), new Punch(at("13:30"), at("17:30")));
        assertEquals(240, result.requiredWorkMinutes());
        assertEquals(240, result.workedCountedMinutes());
        assertEquals(240, result.annualLeaveMinutes());
        assertEquals(0, result.roundedLateMinutes());
        assertEquals(480, result.payableMinutes());
    }

    @Test
    void unpaidLeaveDoesNotGeneratePaidCreditAndOtherPaidLeaveDoes() {
        var unpaid = calculate(List.of(leave(FULL_DAY, LeaveType.UNPAID)), Punch.none());
        assertEquals(480, unpaid.unpaidLeaveMinutes());
        assertEquals(0, unpaid.payableMinutes());
        var paid = calculate(List.of(leave(FULL_DAY, LeaveType.OTHER_PAID)), Punch.none());
        assertEquals(480, paid.otherPaidLeaveMinutes());
        assertEquals(0, paid.annualLeaveMinutes());
        assertEquals(480, paid.payableMinutes());
    }

    @Test
    void mixedPaidAndUnpaidHalfDaysAreNotDoubleCounted() {
        var result = calculate(List.of(leave("MORNING", LeaveType.ANNUAL_PAID),
                new Leave("unpaid", "AFTERNOON", LeaveType.UNPAID, Approval.APPROVED)), Punch.none());
        assertEquals(240, result.payableMinutes());
        assertEquals(240, result.unpaidLeaveMinutes());
        assertEquals(new BigDecimal("0.500000"), result.payableDays());
    }

    @Test
    void pendingRejectedCancelledLeavesDoNotReduceRequiredWork() {
        for (var status : List.of(Approval.PENDING, Approval.REJECTED, Approval.CANCELLED)) {
            var result = calculate(List.of(new Leave("leave", FULL_DAY, LeaveType.ANNUAL_PAID, status)), new Punch(at("08:00"), at("17:30")));
            assertEquals(480, result.requiredWorkMinutes());
            assertEquals(0, result.annualLeaveMinutes());
            assertEquals(status == Approval.PENDING ? List.of("leave") : List.of(), result.pendingLeaveIds());
        }
    }

    @Test
    void weekendsWithoutScheduledWorkDoNotAddHolidayCreditOrConsumeLeave() {
        var day = new WorkTimeInput(DATE, ZONE, List.of(), CalendarDay.PAID_HOLIDAY,
                List.of(leave(FULL_DAY, LeaveType.ANNUAL_PAID)), Punch.none(), List.of(), null, at("23:00"), WorkTimePolicy.standard());
        var result = WorkTimeSupport.calculate(day);
        assertEquals(Status.NO_SCHEDULE, result.status());
        assertEquals(0, result.payableMinutes());
        assertEquals(0, result.annualLeaveMinutes());
        assertEquals(0, result.paidHolidayMinutes());
        assertTrue(result.appliedLeaveIds().isEmpty());
    }

    @Test
    void missingAttendanceStaysUnknownButPaidLeaveBreakdownRemainsAvailable() {
        var result = calculate(List.of(leave("MORNING", LeaveType.ANNUAL_PAID)), new Punch(at("13:30"), null));
        assertEquals(Status.MISSING_CHECK_OUT, result.status());
        assertEquals(240, result.annualLeaveMinutes());
        assertNull(result.workedCountedMinutes());
        assertNull(result.payableMinutes());
        assertNull(result.payableDays());
        assertFalse(result.complete());
        assertEquals(Status.NO_RECORD, calculate(List.of(), Punch.none()).status());
    }

    @Test
    void reportsOpenAndNotStartedBeforeRecordingCutoff() {
        var base = input(CalendarDay.WORKDAY, List.of(), Punch.none(), List.of());
        var day = new WorkTimeInput(DATE, ZONE, base.schedule(), base.calendarDay(), base.leaves(),
                Punch.none(), List.of(), base.recordingClosesAt(), at("10:00"), base.policy());
        assertEquals(Status.NOT_STARTED, WorkTimeSupport.calculate(day).status());
        day = new WorkTimeInput(DATE, ZONE, base.schedule(), base.calendarDay(), base.leaves(),
                new Punch(at("08:00"), null), List.of(), base.recordingClosesAt(), at("10:00"), base.policy());
        assertEquals(Status.OPEN, WorkTimeSupport.calculate(day).status());
    }

    @Test
    void overlapsUnknownPeriodsAndAttendanceDuringLeaveRequireReconciliation() {
        var overlap = calculate(List.of(leave(FULL_DAY, LeaveType.ANNUAL_PAID),
                new Leave("second", "MORNING", LeaveType.UNPAID, Approval.APPROVED)), Punch.none());
        assertEquals(Status.SOURCE_CONFLICT, overlap.status());
        assertNull(overlap.payableMinutes());
        assertNull(overlap.paidAbsenceDays());
        assertEquals(Status.SOURCE_CONFLICT, calculate(List.of(leave("NIGHT", LeaveType.ANNUAL_PAID)), Punch.none()).status());
        assertEquals(Status.SOURCE_CONFLICT, calculate(List.of(leave(FULL_DAY, LeaveType.ANNUAL_PAID)), new Punch(at("08:00"), at("17:30"))).status());
    }

    @Test
    void reversedCheckoutWithoutCheckinAndFuturePunchesAreInvalid() {
        for (var punch : List.of(new Punch(at("09:00"), at("08:00")), new Punch(null, at("17:30")),
                new Punch(at("08:00"), at(DATE.plusDays(1), "08:00")))) {
            var result = calculate(List.of(), punch);
            assertEquals(Status.INVALID_RECORD, result.status());
            assertNull(result.payableMinutes());
        }
    }

    @Test
    void overnightShiftBelongsToStartDate() {
        var next = DATE.plusDays(1);
        var day = new WorkTimeInput(DATE, ZONE, List.of(new WorkInterval("NIGHT", at("22:00"), at(next, "06:00"))),
                CalendarDay.WORKDAY, List.of(), new Punch(at("22:08"), at(next, "06:00")), List.of(), at(next, "07:00"),
                at(next, "08:00"), WorkTimePolicy.standard());
        var result = WorkTimeSupport.calculate(day);
        assertEquals(DATE, result.workDate());
        assertEquals(480, result.scheduledMinutes());
        assertEquals(465, result.payableMinutes());
    }

    @Test
    void approvedOvertimeIsCappedToApprovedWindowAndMinutesAreFloored() {
        var overtime = new Overtime("ot", Approval.APPROVED, at("18:00"), at("20:00"), new Punch(at("18:10:30"), at("20:30")));
        var result = WorkTimeSupport.calculate(input(CalendarDay.WORKDAY, List.of(), new Punch(at("08:00"), at("17:30")), List.of(overtime)));
        assertEquals(120, result.approvedOvertimeMinutes());
        assertEquals(109, result.countedOvertimeMinutes());
        assertEquals(480, result.payableMinutes());
        assertEquals(6570L, result.overtime().getFirst().actualSeconds());
    }

    @Test
    void pendingOvertimeNeverCountsAndMissingApprovedCheckoutRemainsUnknown() {
        var pending = new Overtime("ot", Approval.PENDING, at("18:00"), at("20:00"), new Punch(at("18:00"), at("20:00")));
        assertEquals(0, WorkTimeSupport.calculate(input(CalendarDay.WORKDAY, List.of(), new Punch(at("08:00"), at("17:30")), List.of(pending))).countedOvertimeMinutes());
        var open = new Overtime("ot", Approval.APPROVED, at("18:00"), at("20:00"), new Punch(at("18:00"), null));
        var result = WorkTimeSupport.calculate(input(CalendarDay.WORKDAY, List.of(), new Punch(at("08:00"), at("17:30")), List.of(open)));
        assertNull(result.countedOvertimeMinutes());
        assertEquals(480, result.payableMinutes());
        assertFalse(result.complete());
    }

    @Test
    void overtimeCannotOverlapOrdinaryShiftOrAnotherApprovedOvertime() {
        var a = new Overtime("a", Approval.APPROVED, at("18:00"), at("20:00"), new Punch(at("18:00"), at("20:00")));
        var b = new Overtime("b", Approval.APPROVED, at("19:00"), at("21:00"), new Punch(at("19:00"), at("21:00")));
        var result = WorkTimeSupport.calculate(input(CalendarDay.WORKDAY, List.of(), new Punch(at("08:00"), at("17:30")), List.of(a, b)));
        assertNull(result.countedOvertimeMinutes());
        assertTrue(result.overtime().stream().allMatch(r -> r.status() == OvertimeStatus.SOURCE_CONFLICT));
        var duringShift = new Overtime("ot", Approval.APPROVED, at("16:00"), at("18:00"), Punch.none());
        assertNull(WorkTimeSupport.calculate(input(CalendarDay.WORKDAY, List.of(), Punch.none(), List.of(duringShift))).countedOvertimeMinutes());
    }

    @Test
    void workingApprovedOtOnPaidHolidayKeepsHolidayAndOtSeparate() {
        var ot = new Overtime("holiday-ot", Approval.APPROVED, at("08:00"), at("12:00"), new Punch(at("08:00"), at("12:00")));
        var result = WorkTimeSupport.calculate(input(CalendarDay.PAID_HOLIDAY, List.of(), Punch.none(), List.of(ot)));
        assertEquals(480, result.payableMinutes());
        assertEquals(240, result.countedOvertimeMinutes());
        assertEquals(0, result.workedCountedMinutes());
    }

    @Test
    void denominatorIsConfigurableForPartTimeAndUnequalPeriods() {
        var shift = List.of(new WorkInterval("MORNING", at("08:00"), at("12:00")), new WorkInterval("AFTERNOON", at("13:00"), at("15:00")));
        for (var basis : WorkTimePolicy.DayBasis.values()) {
            var day = new WorkTimeInput(DATE, ZONE, shift, CalendarDay.WORKDAY,
                    List.of(leave("MORNING", LeaveType.ANNUAL_PAID)), new Punch(at("13:00"), at("15:00")), List.of(), at("22:00"), at("23:00"), new WorkTimePolicy(480, 15, basis));
            var result = WorkTimeSupport.calculate(day);
            assertEquals(360, result.payableMinutes());
            assertEquals(new BigDecimal(basis == WorkTimePolicy.DayBasis.STANDARD_DAY ? "0.750000" : "1.000000"), result.payableDays());
        }
    }

    @Test
    void periodDoesNotHideMissingDataOrDoubleCountDates() {
        var first = input(CalendarDay.PAID_HOLIDAY, List.of(), Punch.none(), List.of());
        var date = DATE.plusDays(1);
        var second = new WorkTimeInput(date, ZONE, schedule(date), CalendarDay.WORKDAY, List.of(),
                new Punch(at(date, "08:00"), null), List.of(), at(date, "22:00"), at(date, "23:00"), WorkTimePolicy.standard());
        var result = WorkTimeSupport.calculatePeriod(List.of(second, first));
        assertEquals(List.of(date), result.incompleteDates());
        assertEquals(480, result.knownPayableMinutes());
        assertNull(result.payableMinutes());
        assertNull(result.payableDays());
        assertEquals(DATE, result.days().getFirst().workDate());
        assertThrows(IllegalArgumentException.class, () -> WorkTimeSupport.calculatePeriod(List.of(first, first)));
        assertEquals(new BigDecimal("1.000000"), WorkTimeSupport.calculatePeriod(List.of(first)).payableDays());
    }

    @Test
    void invalidScheduleAndDuplicateRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorkTimePolicy(0, 15, WorkTimePolicy.DayBasis.STANDARD_DAY));
        assertThrows(IllegalArgumentException.class, () -> new WorkInterval("MORNING", at("08:00:01"), at("12:00")));
        var leave = leave(FULL_DAY, LeaveType.ANNUAL_PAID);
        assertThrows(IllegalArgumentException.class, () -> calculate(List.of(leave, leave), Punch.none()));
        var bad = new WorkTimeInput(DATE, ZONE, List.of(new WorkInterval("MORNING", at("08:00"), at("14:00")),
                new WorkInterval("AFTERNOON", at("13:30"), at("17:30"))), CalendarDay.WORKDAY, List.of(), Punch.none(), List.of(), at("22:00"), at("23:00"), WorkTimePolicy.standard());
        assertThrows(IllegalArgumentException.class, () -> WorkTimeSupport.calculate(bad));
    }

    @Test
    void roundingBoundariesAndShortShiftNeverCreateNegativePay() {
        var policy = WorkTimePolicy.standard();
        assertEquals(0, policy.roundDeviation(0));
        assertEquals(15, policy.roundDeviation(1));
        assertEquals(15, policy.roundDeviation(900));
        assertEquals(30, policy.roundDeviation(901));
        var shortShift = new WorkTimeInput(DATE, ZONE, List.of(new WorkInterval("SHORT", at("08:00"), at("08:10"))),
                CalendarDay.WORKDAY, List.of(), new Punch(at("08:04"), at("08:06")), List.of(), at("09:00"), at("10:00"), policy);
        assertEquals(0, WorkTimeSupport.calculate(shortShift).payableMinutes());
    }
    @Test
    void periodRejectsOvernightOverlapAcrossDifferentWorkDates() {
        var next = DATE.plusDays(1);
        var night = new WorkTimeInput(DATE, ZONE, List.of(new WorkInterval("NIGHT", at("22:00"), at(next, "06:00"))),
                CalendarDay.WORKDAY, List.of(), Punch.none(), List.of(), at(next, "07:00"), at(next, "23:00"), WorkTimePolicy.standard());
        var ot = new Overtime("early-ot", Approval.APPROVED, at(next, "05:00"), at(next, "07:00"), Punch.none());
        var following = new WorkTimeInput(next, ZONE, schedule(next), CalendarDay.WORKDAY, List.of(), Punch.none(),
                List.of(ot), at(next, "22:00"), at(next, "23:00"), WorkTimePolicy.standard());
        assertThrows(IllegalArgumentException.class, () -> WorkTimeSupport.calculatePeriod(List.of(night, following)));
    }

    @Test
    void unscheduledDayCanHaveSeparateApprovedOvertimeCrossingMidnight() {
        var next = DATE.plusDays(1);
        var ot = new Overtime("night-ot", Approval.APPROVED, at("23:00"), at(next, "01:00"), new Punch(at("23:10"), at(next, "01:30")));
        var day = new WorkTimeInput(DATE, ZONE, List.of(), CalendarDay.WORKDAY, List.of(), Punch.none(),
                List.of(ot), null, at(next, "02:00"), WorkTimePolicy.standard());
        var result = WorkTimeSupport.calculate(day);
        assertEquals(0, result.payableMinutes());
        assertEquals(110, result.countedOvertimeMinutes());
        assertTrue(result.complete());
    }

}
