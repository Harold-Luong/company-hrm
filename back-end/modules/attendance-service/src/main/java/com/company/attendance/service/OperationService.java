package com.company.attendance.service;

import com.company.attendance.entity.AttendanceOperation;
import com.company.attendance.repository.AttendanceOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import static com.company.attendance.service.ApiRules.*;

@Service
@RequiredArgsConstructor
public class OperationService {
    private final AttendanceOperationRepository operations;
    private final ObjectMapper mapper;
    private final Clock clock;

    public <T> T replay(String actor, String type, String key, Object body, Class<T> resultType) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}"))
            throw error(HttpStatus.BAD_REQUEST, "A 1-100 character Idempotency-Key is required");
        var found = operations.findByActorUserIdAndOperationTypeAndRequestKey(actor, type, key);
        if (found.isEmpty())
            return null;
        if (!mapper.readTree(found.get().getRequestBody()).equals(mapper.readTree(mapper.writeValueAsString(body))))
            throw error(HttpStatus.CONFLICT, "Idempotency-Key was used for a different request");
        return mapper.readValue(found.get().getResponseBody(), resultType);
    }

    public UUID save(String actor, String type, String key, Object body, Object response) {
        var operation = new AttendanceOperation();
        operation.setId(UUID.randomUUID());
        operation.setActorUserId(actor);
        operation.setOperationType(type);
        operation.setRequestKey(key);
        operation.setRequestBody(mapper.writeValueAsString(body));
        operation.setResponseBody(mapper.writeValueAsString(response));
        operation.setOccurredAt(clock.instant());
        operations.save(operation);
        return operation.getId();
    }
}
