package dev.guardia.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A message that has been decided but not yet announced.
 *
 * <p>The row is written in the same transaction as the state change it describes, so the
 * database can never disagree with what the rest of the system was told. Publishing happens
 * afterwards, from {@link OutboxPublisher}, and may therefore happen twice if the process
 * dies between the send and the mark. That is deliberate: duplicates are cheap to absorb
 * on the consumer side, and a lost event is not.
 */
@Entity
@Table(name = "outbox")
public class OutboxMessage {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Becomes the Kafka key, so every event about one aggregate lands on one partition, in order. */
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	@Column(name = "topic", nullable = false, updatable = false)
	private String topic;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxMessage() {
	}

	OutboxMessage(UUID id, UUID aggregateId, String topic, String eventType, String payload, Instant createdAt) {
		this.id = id;
		this.aggregateId = aggregateId;
		this.topic = topic;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	public UUID id() {
		return id;
	}

	public UUID aggregateId() {
		return aggregateId;
	}

	public String topic() {
		return topic;
	}

	public String eventType() {
		return eventType;
	}

	public String payload() {
		return payload;
	}

	public Instant publishedAt() {
		return publishedAt;
	}

	void markPublished(Instant at) {
		this.publishedAt = at;
	}
}
