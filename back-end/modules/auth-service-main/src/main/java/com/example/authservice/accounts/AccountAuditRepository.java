package com.example.authservice.accounts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuditRepository extends JpaRepository<AccountAudit, Long> {
    Page<AccountAudit> findByAccountId(Long accountId, Pageable pageable);
}
