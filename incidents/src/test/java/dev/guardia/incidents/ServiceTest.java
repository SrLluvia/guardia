package dev.guardia.incidents;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real Postgres, the real migrations, and no Kafka.
 *
 * <p>The database is real because the things worth testing here — one slot per incident, the
 * outbox and the state change committing together — are database behaviour, and an in-memory
 * stand-in would only prove that the stand-in agrees with itself. Kafka is left out on
 * purpose: these tests drive the services directly and read the outbox table, which is the
 * boundary this service is responsible for. Whether the bytes then reach a broker is the
 * end-to-end suite's question.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.kafka.listener.auto-startup=false",
		"guardia.outbox.poll-interval=3600000"
})
public abstract class ServiceTest {

	/**
	 * Started once and left running for every test class in this module, rather than being
	 * stopped and restarted around each one. Testcontainers' own cleanup container removes it
	 * when the build ends.
	 */
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	static {
		POSTGRES.start();
	}
}
