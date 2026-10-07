package com.company.attendance;

import com.company.attendance.dto.ShiftRequest;
import com.company.attendance.enums.*;
import com.company.attendance.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AttendanceCalculatorTests {
    private final AttendanceCalculator calculator = new AttendanceCalculator();
    private final LocalDate date = LocalDate.of(2030, 1, 7);
    private Instant at(String time) { return date.atTime(LocalTime.parse(time)).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
    private ShiftRequest shift(List<ShiftRequest.Interval> intervals) {
        return new ShiftRequest("Default", AttendanceMode.FIXED_SHIFT, "Asia/Ho_Chi_Minh", intervals, LocalTime.of(6, 0), LocalTime.of(22, 0));
    }
    private ShiftRequest.Interval interval(WorkPeriod period, String from, String until) {
        return new ShiftRequest.Interval(period, LocalTime.parse(from), LocalTime.parse(until));
    }
    @ParameterizedTest
    @CsvSource({"0,0", "300,15", "900,15", "901,30", "960,30", "1800,30", "1860,45"})
    void roundsPositiveDeviationUpWithoutTruncatingSeconds(long seconds, int rounded) {
        assertThat(AttendanceCalculator.round15(seconds)).isEqualTo(rounded);
    }
    @Test
    void lunchDoesNotCountAndStayingLateDoesNotOffsetLateArrival() {
        var schedule = shift(List.of(interval(WorkPeriod.MORNING, "08:00", "12:00"), interval(WorkPeriod.AFTERNOON, "13:30", "17:30")));
        var result = calculator.calculate(date, schedule, at("08:08"), at("17:38"), List.of(), List.of(), at("18:00"));
        assertThat(result.base()).isEqualTo(480);
        assertThat(result.actualSeconds()).isEqualTo(28320);
        assertThat(result.counted()).isEqualTo(465);
    }
    @Test
    void roundsLateAndEarlySeparatelyAndNeverProducesNegativeWork() {
        var schedule = shift(List.of(interval(WorkPeriod.AFTERNOON, "13:00", "15:00")));
        var result = calculator.calculate(date, schedule, at("13:05"), at("14:55"), List.of(), List.of(), at("18:00"));
        assertThat(result.counted()).isEqualTo(90);
        var shortShift = shift(List.of(interval(WorkPeriod.AFTERNOON, "13:00", "13:10")));
        assertThat(calculator.calculate(date, shortShift, at("13:04"), at("13:06"), List.of(), List.of(), at("18:00")).counted()).isZero();
    }
    @Test
    void approvedFullDayAndHolidayNeedNoInventedPunch() {
        var schedule = shift(List.of(interval(WorkPeriod.AFTERNOON, "13:00", "15:30")));
        var leave = new WorkforceClient.Leave(UUID.randomUUID(), UUID.randomUUID(), "ANNUAL", date, date, "FULL_DAY", "APPROVED", 1);
        var result = calculator.calculate(date, schedule, null, null, List.of(leave), List.of(), at("18:00"));
        assertThat(result.status()).isEqualTo("ON_LEAVE"); assertThat(result.annual()).isEqualTo(150);
        var holiday = new WorkforceClient.Holiday(1L, "HOLIDAY", "PUBLISHED", "ALL", true, date, date);
        var nonWorking = calculator.calculate(date, schedule, null, null, List.of(leave), List.of(holiday), at("18:00"));
        assertThat(nonWorking.status()).isEqualTo("HOLIDAY"); assertThat(nonWorking.annual()).isZero();
    }
    @Test
    void leaveForAMissingPeriodRequiresReconciliation() {
        var schedule = shift(List.of(interval(WorkPeriod.AFTERNOON, "13:00", "15:00")));
        var leave = new WorkforceClient.Leave(UUID.randomUUID(), UUID.randomUUID(), "ANNUAL", date, date, "MORNING", "APPROVED", 1);
        assertThat(calculator.calculate(date, schedule, at("13:00"), at("15:00"), List.of(leave), List.of(), at("18:00")).status())
                .isEqualTo("SOURCE_CONFLICT");
    }
    @Test
    void pendingLeaveDoesNotReduceWorkAndOverlappingCoverageIsNotDoubleCounted() {
        var schedule = shift(List.of(interval(WorkPeriod.AFTERNOON, "13:00", "15:00")));
        UUID id = UUID.randomUUID(), employee = UUID.randomUUID();
        var pending = new WorkforceClient.Leave(id, employee, "ANNUAL", date, date, "FULL_DAY", "PENDING", 0);
        var result = calculator.calculate(date, schedule, at("13:00"), at("15:00"), List.of(pending), List.of(), at("18:00"));
        assertThat(result.counted()).isEqualTo(120); assertThat(result.pending()).containsExactly(id);
        var approved = new WorkforceClient.Leave(id, employee, "ANNUAL", date, date, "FULL_DAY", "APPROVED", 1);
        assertThat(calculator.calculate(date, schedule, null, null, List.of(approved, approved), List.of(), at("18:00")).status()).isEqualTo("SOURCE_CONFLICT");
    }
}
