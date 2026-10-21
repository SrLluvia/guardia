package dev.guardia.e2e;

import dev.guardia.incidents.IncidentsApplication;
import dev.guardia.scheduling.SchedulingApplication;
import dev.guardia.sla.SlaApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole system, in one JVM, against a real broker and a real database.
 *
 * <p>Each service runs as its own Spring context with its own schema and its own consumer
 * group, exactly as it would in its own container. What it does not get is its own
 * {@code application.yml}: three services on one classpath means one file would win and the
 * other two would silently inherit it, so configuration is passed in here instead. The
 * duplication is the price of running them together, and it is confined to this class.
 */
final class GuardiaCluster {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
	static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

	private static ConfigurableApplicationContext incidents;
	private static ConfigurableApplicationContext scheduling;
	private static ConfigurableApplicationContext sla;
	private static boolean started;

	private GuardiaCluster() {
	}

	static synchronized void start() {
		if (started) {
			return;
		}
		POSTGRES.start();
		KAFKA.start();
		// Started in the order the conversation flows, so the first incident of a test cannot
		// be published before the service meant to react to it is listening.
		scheduling = run(SchedulingApplication.class, "scheduling", Map.of(
				"guardia.hold.ttl", "PT2M",
				"guardia.hold.sweep-interval", "1000",
				// Far enough out that a 15 minute response time cannot be met by accident.
				"guardia.agenda.lead-time", "PT6H"));
		sla = run(SlaApplication.class, "sla", Map.of());
		incidents = run(IncidentsApplication.class, "incidents", Map.of());
		started = true;

		Runtime.getRuntime().addShutdownHook(new Thread(GuardiaCluster::stop));
	}

	static void stop() {
		for (ConfigurableApplicationContext context : new ConfigurableApplicationContext[] {incidents, sla, scheduling}) {
			if (context != null && context.isActive()) {
				context.close();
			}
		}
	}

	static String incidentsBaseUrl() {
		return baseUrlOf(incidents);
	}

	static String schedulingBaseUrl() {
		return baseUrlOf(scheduling);
	}

	private static String baseUrlOf(ConfigurableApplicationContext context) {
		return "http://localhost:" + context.getEnvironment().getRequiredProperty("local.server.port");
	}

	static <T> T bean(String service, Class<T> type) {
		return switch (service) {
			case "incidents" -> incidents.getBean(type);
			case "scheduling" -> scheduling.getBean(type);
			case "sla" -> sla.getBean(type);
			default -> throw new IllegalArgumentException("no such service: " + service);
		};
	}

	private static ConfigurableApplicationContext run(Class<?> application, String name, Map<String, String> extra) {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("spring.application.name", name);
		properties.put("server.port", 0);

		properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
		properties.put("spring.datasource.username", POSTGRES.getUsername());
		properties.put("spring.datasource.password", POSTGRES.getPassword());
		properties.put("spring.jpa.properties.hibernate.default_schema", name);
		properties.put("spring.jpa.hibernate.ddl-auto", "validate");
		properties.put("spring.jpa.open-in-view", false);
		properties.put("spring.flyway.locations", "classpath:db/migration/" + name);
		properties.put("spring.flyway.schemas", name);
		properties.put("spring.flyway.default-schema", name);

		properties.put("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
		properties.put("spring.kafka.producer.key-serializer", "org.apache.kafka.common.serialization.StringSerializer");
		properties.put("spring.kafka.producer.value-serializer", "org.apache.kafka.common.serialization.StringSerializer");
		properties.put("spring.kafka.producer.acks", "all");
		properties.put("spring.kafka.producer.properties.enable.idempotence", true);
		properties.put("spring.kafka.consumer.group-id", name);
		properties.put("spring.kafka.consumer.auto-offset-reset", "earliest");
		properties.put("spring.kafka.consumer.enable-auto-commit", false);
		properties.put("spring.kafka.consumer.key-deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		properties.put("spring.kafka.consumer.value-deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		properties.put("spring.kafka.listener.ack-mode", "record");

		properties.put("guardia.outbox.poll-interval", "200");
		properties.putAll(extra);

		// Passed as command line arguments rather than through properties(), which lands in
		// Spring's default properties and therefore loses to any application.yml on the
		// classpath — and with three services there, one of those files always wins.
		String[] arguments = properties.entrySet().stream()
				.map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
				.toArray(String[]::new);

		return new SpringApplicationBuilder(application).run(arguments);
	}
}
