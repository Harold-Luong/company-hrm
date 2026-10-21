package com.company.leave.request;

import com.company.leave.request.LeaveModels.History;
import com.company.leave.request.LeaveModels.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaveHistoryActionTests {
    @ParameterizedTest
    @CsvSource({
        "APPROVED, APPROVED, ADJUSTMENT_APPROVED",
        "REJECTED, REJECTED, ADJUSTMENT_REJECTED",
        "CANCELLED, CANCELLED, ADJUSTMENT_CANCELLED"
    })
    void mapsDecisionsExplicitly(Status status, LeaveHistoryAction request, LeaveHistoryAction adjustment) {
        assertThat(LeaveHistoryAction.forDecision(status)).isEqualTo(request);
        assertThat(LeaveHistoryAction.forAdjustmentDecision(status)).isEqualTo(adjustment);
    }

    @Test
    void pendingCannotBecomeADecisionHistoryEntry() {
        assertThatIllegalArgumentException().isThrownBy(() -> LeaveHistoryAction.forDecision(Status.PENDING));
        assertThatIllegalArgumentException().isThrownBy(() -> LeaveHistoryAction.forAdjustmentDecision(Status.PENDING));
    }

    @ParameterizedTest
    @CsvSource({
        "SUBMITTED, SUBMITTED",
        "APPROVED, APPROVED",
        "REJECTED, REJECTED",
        "CANCELLED, CANCELLED",
        "ADJUSTMENT_SUBMITTED, ADJUSTMENT_SUBMITTED",
        "ADJUSTMENT_APPROVED, ADJUSTMENT_APPROVED",
        "ADJUSTMENT_REJECTED, ADJUSTMENT_REJECTED",
        "ADJUSTMENT_CANCELLED, ADJUSTMENT_CANCELLED"
    })
    void preservesDatabaseAndJsonCodes(LeaveHistoryAction action, String storedCode) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LeaveRepository repository = new LeaveRepository(jdbc);
        UUID requestId = UUID.randomUUID();

        repository.audit(requestId, action, "actor", "note");
        verify(jdbc).update(
                "INSERT INTO leave_request_history(request_id, action, actor_user_id, note) VALUES (?, ?, ?, ?)",
                requestId, storedCode, "actor", "note");

        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(1L);
        when(row.getString("action")).thenReturn(storedCode);
        when(row.getString("actor_user_id")).thenReturn("actor");
        when(row.getString("note")).thenReturn("note");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<History>>any(), eq(requestId)))
                .thenAnswer(invocation -> {
                    RowMapper<History> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        History history = repository.history(requestId).getFirst();
        assertThat(history.action()).isEqualTo(action);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(history);
        assertThat(mapper.readTree(json).get("action").asString()).isEqualTo(storedCode);
        assertThat(mapper.readValue(json, History.class).action()).isEqualTo(action);
    }
}
