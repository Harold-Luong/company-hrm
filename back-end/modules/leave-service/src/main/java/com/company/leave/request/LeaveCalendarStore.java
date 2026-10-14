package com.company.leave.request;

import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Repository
public class LeaveCalendarStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public LeaveCalendarStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public void save(UUID id, List<LeaveCalendarClient.Day> days) {
        jdbc.update("INSERT INTO leave_calendar_snapshots(request_id, calendar_json) VALUES (?, ?)", id, mapper.writeValueAsString(days));
    }
    public Optional<List<LeaveCalendarClient.Day>> find(UUID id) {
        return jdbc.query("SELECT calendar_json FROM leave_calendar_snapshots WHERE request_id = ?",
            (rs, i) -> List.of(mapper.readValue(rs.getString(1), LeaveCalendarClient.Day[].class)), id).stream().findFirst();
    }
}
