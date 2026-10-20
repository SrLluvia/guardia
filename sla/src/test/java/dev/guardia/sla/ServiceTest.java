package dev.guardia.sla;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A real Postgres with the real migrations, and no broker: see the incidents module for why. */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.kafka.listener.auto-startup=false",
		"guardia.outbox.poll-interval=3600000",
		"guardia.hold.sweep-interval=3600000",
		"guardia.agenda.top-up-interval=3600000"
})
public abstract class ServiceTest {

	/** One container for the whole module's tests; Testcontainers removes it when the build ends. */
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	static {
		POSTGRES.start();
	}
}
