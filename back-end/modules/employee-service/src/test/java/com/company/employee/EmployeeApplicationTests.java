package com.company.employee;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeApplicationTests extends JwtTestSupport {

	@Test
	void contextLoads() {
	}

}
