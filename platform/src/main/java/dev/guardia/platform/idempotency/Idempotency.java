package dev.guardia.platform.idempotency;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs a piece of consumer work at most once, however many times Kafka delivers the message. */
@Component
public class Idempotency {

	private static final Logger log = LoggerFactory.getLogger(Idempotency.class);

	private final ProcessedMessageRepository repository;
	private final Clock clock;

	public Idempotency(ProcessedMessageRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * @return true when the work ran, false when this message had already been handled
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public boolean once(UUID messageId, Runnable work) {
		if (repository.existsById(messageId)) {
			log.debug("message {} already handled, skipping", messageId);
			return false;
		}
		// Flushed before the work so the primary key, not the check above, is the real guard:
		// two concurrent deliveries of one message cannot both get past it.
		repository.saveAndFlush(new ProcessedMessage(messageId, Instant.now(clock)));
		work.run();
		return true;
	}
}
