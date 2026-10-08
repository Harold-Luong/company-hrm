package com.company.attendance.service;

import com.company.attendance.entity.AttendanceOperation;
import com.company.attendance.repository.AttendanceOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static com.company.attendance.service.ApiRules.*;

@Service
@RequiredArgsConstructor
public class OperationService {
    private final AttendanceOperationRepository operations;
    private final ObjectMapper mapper;
    private final Clock clock;
    @Value("${attendance.idempotency-response-retention-days:180}")
    private int retentionDays;

    public <T> T replay(String actor, String type, String key, Object body, Class<T> resultType) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}"))
            throw error(HttpStatus.BAD_REQUEST, "A 1-100 character Idempotency-Key is required");
        var found = operations.findByActorUserIdAndOperationTypeAndRequestKey(actor, type, key);
        if (found.isEmpty())
            return null;
        if (!mapper.readTree(found.get().getRequestBody()).equals(mapper.readTree(mapper.writeValueAsString(body))))
            throw error(HttpStatus.CONFLICT, "Idempotency-Key was used for a different request");
        if (found.get().getResponseBody() == null)
            throw error(HttpStatus.CONFLICT, "Idempotency response expired; the original operation will not be repeated");
        return mapper.readValue(found.get().getResponseBody(), resultType);
    }

    public UUID save(String actor, String type, String key, Object body, Object response) {
        var operation = new AttendanceOperation();
        operation.recordAudit(actor, clock.instant());
        operation.setOperationType(type);
        operation.setRequestKey(key);
        operation.setRequestBody(mapper.writeValueAsString(body));
        operation.setResponseBody(mapper.writeValueAsString(response));
        operations.save(operation);
        return operation.getId();
    }

    /** Chỉ dọn payload chấm công cũ; giữ khóa, dữ liệu yêu cầu và toàn bộ audit nghiệp vụ. */
    @Scheduled(cron = "${attendance.idempotency-cleanup-cron:0 0 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void expirePunchResponses() {
        if (retentionDays <= 0) return;
        var ids = operations.expiredResponses(List.of("CHECK_IN", "CHECK_OUT", "OT_CHECK_IN", "OT_CHECK_OUT"),
                clock.instant().minus(retentionDays, ChronoUnit.DAYS), PageRequest.of(0, 500));
        if (!ids.isEmpty()) operations.clearResponses(ids);
    }
}
