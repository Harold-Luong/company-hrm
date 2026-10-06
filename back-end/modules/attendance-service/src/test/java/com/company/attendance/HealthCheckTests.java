package com.company.attendance;

import com.company.attendance.controller.HealthCheckController;
import com.company.attendance.repository.HealthCheckRepository;
import com.company.attendance.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckTests {
    @Test
    void databaseFailureReturnsUnavailableWithoutLeakingDetails() {
        assertDown(new DataAccessResourceFailureException("private database connection details"));
    }

    @Test
    void transactionConnectionFailureReturnsUnavailable() {
        assertDown(new CannotCreateTransactionException("private connection details"));
    }

    private void assertDown(RuntimeException failure) {
        var repository = new HealthCheckRepository() {
            @Override
            public boolean isDatabaseConnected() {
                throw failure;
            }
        };
        var controller = new HealthCheckController(new HealthCheckService(repository));
        var response = controller.healthCheck();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo("DOWN");
        assertThat(response.getBody().database()).isEqualTo("DOWN");
    }
}
