package com.company.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

import java.util.UUID;

/** Auth event sequence, not a JPA optimistic locking version. */
@Entity
@Table(name = "employee_account_versions")
@Check(constraints = "version >= 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAccountVersion {
    @Id
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false)
    private long version;
}
