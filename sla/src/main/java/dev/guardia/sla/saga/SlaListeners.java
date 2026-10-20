package dev.guardia.sla.saga;

import dev.guardia.platform.idempotency.Idempotency;
import dev.guardia.platform.kafka.EventHeaders;
import dev.guardia.platform.kafka.Events;
import dev.guardia.sla.Topics;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SlaListeners {

	private static final Logger log = LoggerFactory.getLogger(SlaListeners.class);

	private final SlaCheck check;
	private final Idempotency idempotency;
	private final Events events;

	public SlaListeners(SlaCheck check, Idempotency idempotency, Events events) {
		this.check = check;
		this.idempotency = idempotency;
		this.events = events;
	}

	@KafkaListener(topics = Topics.SCHEDULING_EVENTS, groupId = "sla")
	@Transactional
	public void onSchedulingEvent(ConsumerRecord<String, String> record) {
		UUID messageId = EventHeaders.messageId(record);
		String type = EventHeaders.eventType(record);
		idempotency.once(messageId, () -> {
			if (SlaEvents.SlotHeld.TYPE.equals(type)) {
				check.onSlotHeld(events.read(record.value(), SlaEvents.SlotHeld.class));
			} else {
				log.trace("no interest in {}", type);
			}
		});
	}
}
