package dev.guardia.scheduling.saga;

import dev.guardia.platform.idempotency.Idempotency;
import dev.guardia.platform.kafka.EventHeaders;
import dev.guardia.platform.kafka.Events;
import dev.guardia.scheduling.Topics;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SchedulingListeners {

	private static final Logger log = LoggerFactory.getLogger(SchedulingListeners.class);

	private final SlotAllocation allocation;
	private final Idempotency idempotency;
	private final Events events;

	public SchedulingListeners(SlotAllocation allocation, Idempotency idempotency, Events events) {
		this.allocation = allocation;
		this.idempotency = idempotency;
		this.events = events;
	}

	@KafkaListener(topics = Topics.INCIDENT_EVENTS, groupId = "scheduling")
	@Transactional
	public void onIncidentEvent(ConsumerRecord<String, String> record) {
		UUID messageId = EventHeaders.messageId(record);
		String type = EventHeaders.eventType(record);
		idempotency.once(messageId, () -> {
			switch (type) {
				case SchedulingEvents.IncidentCreated.TYPE ->
						allocation.onIncidentCreated(events.read(record.value(), SchedulingEvents.IncidentCreated.class));
				case SchedulingEvents.AssignmentConfirmed.TYPE ->
						allocation.onAssignmentConfirmed(events.read(record.value(), SchedulingEvents.AssignmentConfirmed.class));
				case SchedulingEvents.AssignmentCancelled.TYPE ->
						allocation.onAssignmentCancelled(events.read(record.value(), SchedulingEvents.AssignmentCancelled.class));
				default -> log.trace("no interest in {}", type);
			}
		});
	}
}
