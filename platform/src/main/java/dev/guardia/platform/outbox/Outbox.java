package dev.guardia.platform.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Records an event for later publication. Call it inside the transaction that made the decision. */
@Component
public class Outbox {

	private final OutboxRepository repository;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public Outbox(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * @param aggregateId the saga this event belongs to; it becomes the partition key
	 * @param eventType   read by consumers from a header to pick a payload type
	 */
	public UUID append(UUID aggregateId, String topic, String eventType, Object payload) {
		UUID messageId = UUID.randomUUID();
		repository.save(new OutboxMessage(messageId, aggregateId, topic, eventType, serialize(payload), Instant.now(clock)));
		return messageId;
	}

	private String serialize(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("event payload is not serialisable: " + payload.getClass(), e);
		}
	}
}
