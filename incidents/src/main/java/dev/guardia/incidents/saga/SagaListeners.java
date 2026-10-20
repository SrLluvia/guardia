package dev.guardia.incidents.saga;

import dev.guardia.incidents.Topics;
import dev.guardia.platform.idempotency.Idempotency;
import dev.guardia.platform.kafka.EventHeaders;
import dev.guardia.platform.kafka.Events;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where the outside world gets in.
 *
 * <p>One listener per topic, and every delivery passes through {@link Idempotency} first, in
 * the same transaction as the work it guards. Kafka's promise is at-least-once, so the same
 * record will sometimes arrive twice; the second one finds the message already recorded and
 * does nothing.
 *
 * <p>An event type this service does not care about is acknowledged and dropped. Throwing
 * would send a perfectly good message to the dead letter topic for the crime of not being
 * interesting.
 */
@Component
public class SagaListeners {

	private static final Logger log = LoggerFactory.getLogger(SagaListeners.class);

	private final IncidentSaga saga;
	private final Idempotency idempotency;
	private final Events events;

	public SagaListeners(IncidentSaga saga, Idempotency idempotency, Events events) {
		this.saga = saga;
		this.idempotency = idempotency;
		this.events = events;
	}

	@KafkaListener(topics = Topics.SCHEDULING_EVENTS, groupId = "incidents")
	@Transactional
	public void onSchedulingEvent(ConsumerRecord<String, String> record) {
		handle(record, type -> {
			switch (type) {
				case SagaEvents.SlotHeld.TYPE -> saga.onSlotHeld(events.read(record.value(), SagaEvents.SlotHeld.class));
				case SagaEvents.SlotUnavailable.TYPE ->
						saga.onSlotUnavailable(events.read(record.value(), SagaEvents.SlotUnavailable.class));
				case SagaEvents.HoldExpired.TYPE ->
						saga.onHoldExpired(events.read(record.value(), SagaEvents.HoldExpired.class));
				default -> log.trace("no interest in {}", type);
			}
		});
	}

	@KafkaListener(topics = Topics.SLA_EVENTS, groupId = "incidents")
	@Transactional
	public void onSlaEvent(ConsumerRecord<String, String> record) {
		handle(record, type -> {
			switch (type) {
				case SagaEvents.SlaApproved.TYPE -> saga.onSlaApproved(events.read(record.value(), SagaEvents.SlaApproved.class));
				case SagaEvents.SlaRejected.TYPE -> saga.onSlaRejected(events.read(record.value(), SagaEvents.SlaRejected.class));
				default -> log.trace("no interest in {}", type);
			}
		});
	}

	private void handle(ConsumerRecord<String, String> record, java.util.function.Consumer<String> dispatch) {
		UUID messageId = EventHeaders.messageId(record);
		String type = EventHeaders.eventType(record);
		idempotency.once(messageId, () -> dispatch.accept(type));
	}
}
