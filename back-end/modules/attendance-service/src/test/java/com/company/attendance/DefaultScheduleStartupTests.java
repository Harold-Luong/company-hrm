package com.company.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.Clock;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendance-default-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "attendance.initialize-default-schedule=true"
})
@ActiveProfiles("test")
class DefaultScheduleStartupTests extends JwtTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    void startupPersistsTheWeekdayDefaultWithoutAnHrApiCall() {
        assertThat(jdbc.queryForList("SELECT weekday FROM work_schedule_rules WHERE employee_id IS NULL ORDER BY weekday", Integer.class))
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(jdbc.queryForList("SELECT effective_from FROM work_schedule_rules", LocalDate.class))
                .allMatch(date -> date.equals(LocalDate.now(clock)));
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM attendance_schedule_batches", String.class))
                .isEqualTo("SYSTEM_DEFAULT_SCHEDULE");
    }
}
