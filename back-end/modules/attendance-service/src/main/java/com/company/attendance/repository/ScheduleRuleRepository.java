package com.company.attendance.repository;

import com.company.attendance.entity.ScheduleRule;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleRuleRepository extends JpaRepository<ScheduleRule, UUID> {
    boolean existsByEmployeeIdIsNull();
    @Query("select r from ScheduleRule r where r.effectiveFrom <= :until and r.effectiveUntil >= :from")
    @EntityGraph(attributePaths = "shiftRevision")
    List<ScheduleRule> overlapping(LocalDate from, LocalDate until);
    @Query("select r from ScheduleRule r where (r.employeeId is null or r.employeeId = :employee) and r.weekday = :weekday and r.effectiveFrom <= :date and r.effectiveUntil >= :date")
    @EntityGraph(attributePaths = "shiftRevision")
    List<ScheduleRule> effective(UUID employee, LocalDate date, int weekday);
    @Query("select r from ScheduleRule r where (r.employeeId is null or r.employeeId = :employee) and r.effectiveFrom <= :until and r.effectiveUntil >= :from")
    @EntityGraph(attributePaths = "shiftRevision")
    List<ScheduleRule> applicable(UUID employee, LocalDate from, LocalDate until);
}
