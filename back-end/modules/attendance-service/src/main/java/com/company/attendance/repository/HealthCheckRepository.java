package com.company.attendance.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class HealthCheckRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true, timeout = 3)
    public boolean isDatabaseConnected() {
        Number result = (Number) entityManager.createNativeQuery("SELECT 1")
                .setHint("jakarta.persistence.query.timeout", 2000)
                .getSingleResult();
        return result.intValue() == 1;
    }
}
