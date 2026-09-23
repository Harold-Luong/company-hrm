package com.company.employee.repository;

import com.company.employee.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
