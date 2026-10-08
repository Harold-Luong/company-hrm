package com.company.attendance.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Lớp cha dùng chung UUID và version cho các entity có thể cập nhật của Attendance.
 * JPA dùng version để phát hiện ghi đè đồng thời; API đối chiếu giá trị này với If-Match khi cập nhật.
 * Không tạo bảng riêng trong database.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class VersionedEntity {
    @Id
    private UUID id;

    @Version
    private long version;
}
