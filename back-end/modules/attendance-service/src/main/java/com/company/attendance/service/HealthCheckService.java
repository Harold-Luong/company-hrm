package com.company.attendance.service;

import com.company.attendance.repository.HealthCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Service
@RequiredArgsConstructor
public class HealthCheckService {
    private final HealthCheckRepository repository;

    public boolean isDatabaseConnected() {
        try {
            return repository.isDatabaseConnected();
        } catch (DataAccessException | TransactionException exception) {
            return false;
        }
    }
}
