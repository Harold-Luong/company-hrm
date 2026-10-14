package com.company.attendance.repository;
import com.company.attendance.entity.AttendancePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AttendancePeriodRepository extends JpaRepository<AttendancePeriod,String> {}
