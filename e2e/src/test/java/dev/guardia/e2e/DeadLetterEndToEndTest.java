package dev.guardia.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * What a message that can never be processed does to everything behind it.
 *
 * <p>This is the failure that turns a bad record into an outage. A consumer that throws has its
 * record redelivered, and if nothing ever takes that record out of the way it sits at the head
 * of its partition for ever, with every later record stuck behind it. The test puts a record on
 * the topic that cannot be read however many times it is tried, then checks both halves of the
 * answer: the record ends up in the dead letter topic, and an ordinary incident published
 * afterwards still completes.
 */
class DeadLetterEndToEndTest {

	private static final String TOPIC = "incident-events";
	private static final String DEAD_LETTERS = TOPIC + ".DLT";

	private static RestClient client;

	@BeforeAll
	static void startTheSystem() {
		GuardiaCluster.start();
		client = RestClient.create(GuardiaCluster.incidentsBaseUrl());
	}

	@Test
	@DisplayName("a record that cannot be read is set aside, and the queue behind it keeps moving")
	void poisonPillDoesNotStopThePartition() {
		UUID poisonedIncident = UUID.randomUUID();

		// Well-formed envelope, unreadable contents: the headers say this is an incident, so
		// scheduling will try, and will keep failing on the same bytes every time.
		publish(poisonedIncident.toString(), "IncidentCreated", "this is not the JSON you are looking for");

		ConsumerRecord<String, String> deadLettered = awaitDeadLetter();
		assertThat(deadLettered.key())
				.as("the dead letter keeps its key, so a dropped event is still traceable to its saga")
				.isEqualTo(poisonedIncident.toString());
		assertThat(deadLettered.value()).contains("not the JSON");

		// The part that matters. One unreadable record earlier on this partition must not stop
		// a later incident from being dispatched.
		String incidentId = open("acme", "NORMAL", "NETWORK", "Raised after the poison pill");
		await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
				assertThat(status(incidentId))
						.as("the partition must have moved on past the bad record")
						.isEqualTo("ASSIGNED"));
	}

	private ConsumerRecord<String, String> awaitDeadLetter() {
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
			consumer.subscribe(List.of(DEAD_LETTERS));
			// Retries widen to five seconds before the record is given up on, so this waits
			// for longer than the back-off rather than racing it.
			long deadline = System.currentTimeMillis() + Duration.ofSeconds(40).toMillis();
			while (System.currentTimeMillis() < deadline) {
				ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(1));
				if (!polled.isEmpty()) {
					return polled.iterator().next();
				}
			}
			throw new AssertionError("nothing arrived on " + DEAD_LETTERS + " within 40 seconds");
		}
	}

	private void publish(String key, String eventType, String payload) {
		Properties properties = new Properties();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, GuardiaCluster.KAFKA.getBootstrapServers());
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

		try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
			ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
			record.headers().add("event-type", eventType.getBytes(StandardCharsets.UTF_8));
			record.headers().add("message-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
			producer.send(record);
			producer.flush();
		}
	}

	private Properties consumerProperties() {
		Properties properties = new Properties();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, GuardiaCluster.KAFKA.getBootstrapServers());
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-test-" + UUID.randomUUID());
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		return properties;
	}

	private String open(String customerId, String priority, String skill, String summary) {
		Map<?, ?> created = client.post()
				.uri("/api/incidents")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("customerId", customerId, "priority", priority, "requiredSkill", skill, "summary", summary))
				.retrieve()
				.body(Map.class);

		assertThat(created).isNotNull();
		return (String) created.get("id");
	}

	private String status(String incidentId) {
		Map<?, ?> view = client.get().uri("/api/incidents/{id}", incidentId).retrieve().body(Map.class);
		return (String) view.get("status");
	}
}
