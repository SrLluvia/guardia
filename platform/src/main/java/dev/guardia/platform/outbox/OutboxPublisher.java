package dev.guardia.platform.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the outbox onto Kafka.
 *
 * <p>Sends happen one at a time and the row is marked only once the broker has acknowledged,
 * so a crash can repeat a message but cannot lose one. Consumers are idempotent for exactly
 * this reason; see {@code dev.guardia.platform.idempotency}.
 */
@Component
public class OutboxPublisher {

	static final String HEADER_EVENT_TYPE = "event-type";
	static final String HEADER_MESSAGE_ID = "message-id";

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
	private static final int BATCH_SIZE = 50;

	private final OutboxRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final Clock clock;

	public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
		this.repository = repository;
		this.kafkaTemplate = kafkaTemplate;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${guardia.outbox.poll-interval:200}")
	@Transactional
	public void publishPending() {
		List<OutboxMessage> batch = repository.claimUnpublished(Limit.of(BATCH_SIZE));
		for (OutboxMessage message : batch) {
			send(message);
			message.markPublished(Instant.now(clock));
		}
		if (!batch.isEmpty()) {
			log.debug("published {} outbox messages", batch.size());
		}
	}

	private void send(OutboxMessage message) {
		ProducerRecord<String, String> record =
				new ProducerRecord<>(message.topic(), message.aggregateId().toString(), message.payload());
		record.headers().add(HEADER_EVENT_TYPE, message.eventType().getBytes(StandardCharsets.UTF_8));
		record.headers().add(HEADER_MESSAGE_ID, message.id().toString().getBytes(StandardCharsets.UTF_8));
		kafkaTemplate.send(record).join();
	}
}
