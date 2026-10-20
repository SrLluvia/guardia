package dev.guardia.platform.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Proof that a message has already been handled here.
 *
 * <p>The row is written in the same transaction as the work it guards, so "handled" and
 * "recorded as handled" cannot come apart. Each service owns its own schema and each topic
 * has a single listener in it, so the message id alone identifies the work.
 */
@Entity
@Table(name = "processed_message")
public class ProcessedMessage {

	@Id
	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;

	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	protected ProcessedMessage() {
	}

	ProcessedMessage(UUID messageId, Instant processedAt) {
		this.messageId = messageId;
		this.processedAt = processedAt;
	}
}
