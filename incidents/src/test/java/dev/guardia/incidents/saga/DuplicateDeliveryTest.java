package dev.guardia.incidents.saga;

import static org.assertj.core.api.Assertions.assertThat;

import dev.guardia.incidents.ServiceTest;
import dev.guardia.incidents.domain.Incident;
import dev.guardia.incidents.domain.IncidentRepository;
import dev.guardia.incidents.domain.IncidentStatus;
import dev.guardia.incidents.domain.Priority;
import dev.guardia.platform.idempotency.Idempotency;
import dev.guardia.platform.outbox.OutboxMessage;
import dev.guardia.platform.outbox.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Kafka promises at-least-once delivery, so the same record will sometimes arrive twice.
 * These tests deliver it twice on purpose.
 */
class DuplicateDeliveryTest extends ServiceTest {

	@Autowired
	IncidentSaga saga;

	@Autowired
	Idempotency idempotency;

	@Autowired
	IncidentRepository incidents;

	@Autowired
	OutboxRepository outbox;

	@BeforeEach
	void clean() {
		outbox.deleteAll();
		incidents.deleteAll();
	}

	@Test
	@DisplayName("the same message delivered twice is acted on once")
	void secondDeliveryDoesNothing() {
		Incident incident = saga.open("acme", Priority.HIGH, "NETWORK", "Branch office offline");
		saga.onSlotHeld(new SagaEvents.SlotHeld(incident.id(), UUID.randomUUID(), UUID.randomUUID(), "Ane Mendizabal",
				Instant.now().plusSeconds(600)));
		UUID messageId = UUID.randomUUID();
		SagaEvents.SlaApproved approval = new SagaEvents.SlaApproved(incident.id(), 240);

		boolean first = idempotency.once(messageId, () -> saga.onSlaApproved(approval));
		boolean second = idempotency.once(messageId, () -> saga.onSlaApproved(approval));

		assertThat(first).isTrue();
		assertThat(second).as("a repeat delivery must not run the work again").isFalse();
		assertThat(incidents.findById(incident.id()).orElseThrow().status()).isEqualTo(IncidentStatus.ASSIGNED);
		// One confirmation, not two: downstream would otherwise book the slot twice.
		assertThat(outbox.findAll())
				.filteredOn(message -> message.eventType().equals(SagaEvents.AssignmentConfirmed.TYPE))
				.hasSize(1);
	}

	@Test
	@DisplayName("different messages about the same incident are each handled")
	void distinctMessagesAreNotConfusedForDuplicates() {
		Incident incident = saga.open("acme", Priority.HIGH, "NETWORK", "Branch office offline");

		idempotency.once(UUID.randomUUID(), () -> saga.onSlotHeld(
				new SagaEvents.SlotHeld(incident.id(), UUID.randomUUID(), UUID.randomUUID(), "Ane Mendizabal",
						Instant.now().plusSeconds(600))));
		idempotency.once(UUID.randomUUID(), () -> saga.onSlaApproved(new SagaEvents.SlaApproved(incident.id(), 240)));

		assertThat(incidents.findById(incident.id()).orElseThrow().status()).isEqualTo(IncidentStatus.ASSIGNED);
	}

	@Test
	@DisplayName("the guard is the primary key, so the work and the record commit together")
	void workAndItsRecordShareATransaction() {
		UUID messageId = UUID.randomUUID();

		idempotency.once(messageId, () -> saga.open("acme", Priority.NORMAL, "NETWORK", "First"));
		idempotency.once(messageId, () -> saga.open("acme", Priority.NORMAL, "NETWORK", "Second"));

		assertThat(incidents.findAll()).extracting(Incident::summary).containsExactly("First");
		assertThat(outbox.findAll()).extracting(OutboxMessage::eventType)
				.containsExactly(SagaEvents.IncidentCreated.TYPE);
	}
}
