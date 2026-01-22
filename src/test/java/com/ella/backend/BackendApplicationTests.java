package com.ella.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.datasource.url=jdbc:h2:mem:ella_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"jwt.secret=test-secret"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
