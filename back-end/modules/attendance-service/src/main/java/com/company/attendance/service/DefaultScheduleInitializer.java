package com.company.attendance.service;

import com.company.attendance.dto.ScheduleRequest;
import com.company.attendance.entity.ScheduleBatch;
import com.company.attendance.entity.ScheduleRule;
import com.company.attendance.enums.ScheduleScope;
import com.company.attendance.repository.ScheduleBatchRepository;
import com.company.attendance.repository.ScheduleRuleRepository;
import com.company.attendance.repository.ScheduleStateRepository;
import com.company.attendance.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Persists the initial company schedule; HR remains responsible for later changes. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultScheduleInitializer {
    public static final UUID DEFAULT_SHIFT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ScheduleStateRepository state;
    private final ScheduleRuleRepository rules;
    private final ScheduleBatchRepository batches;
    private final WorkShiftRepository shifts;
    private final ShiftService shiftService;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional
    public void initialize() {
        var scheduleState = state.lockState();
        // Even expired/future company rules represent an explicit HR configuration.
        // Never fill their gaps or restore weekdays intentionally omitted by HR.
        if (rules.existsByEmployeeIdIsNull()) return;
        var template = shifts.findById(DEFAULT_SHIFT_ID);
        if (template.isEmpty() || !template.get().isActive()) {
            log.warn("Default schedule was not initialized: the seeded default shift is missing or inactive");
            return;
        }
        var shift = template.get();
        var from = LocalDate.now(clock);
        var weekdays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        var request = new ScheduleRequest(shift.getId(), shift.getVersion(), ScheduleScope.COMPANY_DEFAULT,
                Set.of(), from, null, weekdays, "Tự khởi tạo lịch hành chính thứ Hai–thứ Sáu");
        var batch = new ScheduleBatch();
        batch.recordAudit("SYSTEM_DEFAULT_SCHEDULE", clock.instant());
        batch.setScheduleRevision(scheduleState.getRevision() + 1);
        batch.setRequestBody(mapper.writeValueAsString(request));
        batch.setReplacedRules("[]");
        batches.saveAndFlush(batch);
        var revision = shiftService.revision(shift.getId(), shift.getVersion());
        for (var weekday : weekdays) {
            var rule = new ScheduleRule();
            rule.setId(UUID.randomUUID());
            rule.setBatchId(batch.getId());
            rule.setShiftRevision(revision);
            rule.setWeekday(weekday.getValue());
            rule.setEffectiveFrom(from);
            rule.setEffectiveUntil(LocalDate.of(9999, 12, 31));
            rules.save(rule);
        }
        scheduleState.setRevision(scheduleState.getRevision() + 1);
        log.info("Initialized company default schedule Monday–Friday from {}", from);
    }
}
