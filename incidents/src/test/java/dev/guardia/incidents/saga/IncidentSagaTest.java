package dev.guardia.incidents.saga;

import static org.assertj.core.api.Assertions.assertThat;

import dev.guardia.incidents.ServiceTest;
import dev.guardia.incidents.domain.Incident;
import dev.guardia.incidents.domain.IncidentRepository;
import dev.guardia.incidents.domain.IncidentStatus;
import dev.guardia.incidents.domain.Priority;
import dev.guardia.platform.outbox.OutboxMessage;
import dev.guardia.platform.outbox.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentSagaTest extends ServiceTest {

	@Autowired
	IncidentSaga saga;

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
	@DisplayName("opening an incident records it and the announcement together")
	void opensAndAnnounces() {
		Incident incident = saga.open("acme", Priority.HIGH, "NETWORK", "Branch office offline");

		assertThat(incidents.findById(incident.id()))
				.get()
				.extracting(Incident::status)
				.isEqualTo(IncidentStatus.RECEIVED);

		// Same transaction, so the row and the event either both exist or neither does.
		assertThat(types()).containsExactly(SagaEvents.IncidentCreated.TYPE);
		assertThat(outbox.findAll())
				.singleElement()
				.extracting(OutboxMessage::aggregateId, OutboxMessage::publishedAt)
				.containsExactly(incident.id(), null);
	}

	@Test
	@DisplayName("a held slot and an approved SLA assign the incident")
	void happyPath() {
		Incident incident = saga.open("acme", Priority.HIGH, "NETWORK", "Branch office offline");
		UUID slotId = UUID.randomUUID();

		saga.onSlotHeld(new SagaEvents.SlotHeld(incident.id(), slotId, UUID.randomUUID(), "Ane Mendizabal",
				Instant.now().plusSeconds(1800)));
		assertThat(status(incident)).isEqualTo(IncidentStatus.SLOT_HELD);

		saga.onSlaApproved(new SagaEvents.SlaApproved(incident.id(), 240));

		assertThat(status(incident)).isEqualTo(IncidentStatus.ASSIGNED);
		assertThat(types()).containsExactly(SagaEvents.IncidentCreated.TYPE, SagaEvents.AssignmentConfirmed.TYPE);
		// Who is going is kept, not only mentioned in passing while the hold was open. This
		// service cannot ask scheduling for it later without coupling the two.
		Incident assigned = incidents.findById(incident.id()).orElseThrow();
		assertThat(assigned.technicianName()).isEqualTo("Ane Mendizabal");
		assertThat(assigned.resolutionDetail()).contains("Ane Mendizabal");
	}

	@Test
	@DisplayName("a rejected SLA cancels the incident and names the slot to release")
	void rejectionTriggersCompensation() {
		Incident incident = saga.open("globex", Priority.CRITICAL, "NETWORK", "Core switch down");
		UUID slotId = UUID.randomUUID();
		saga.onSlotHeld(new SagaEvents.SlotHeld(incident.id(), slotId, UUID.randomUUID(), "Bruno Carvalho",
				Instant.now().plusSeconds(7200)));

		saga.onSlaRejected(new SagaEvents.SlaRejected(incident.id(), "Earliest slot is 105 min past the 15 min response time"));

		assertThat(status(incident)).isEqualTo(IncidentStatus.CANCELLED);
		// The cancellation has to carry the slot, or whoever holds it has nothing to act on.
		assertThat(outbox.findAll())
				.filteredOn(message -> message.eventType().equals(SagaEvents.AssignmentCancelled.TYPE))
				.singleElement()
				.satisfies(message -> assertThat(message.payload()).contains(slotId.toString()));
	}

	@Test
	@DisplayName("no technician available cancels without ever holding anything")
	void cancelsWhenNothingCanBeHeld() {
		Incident incident = saga.open("acme", Priority.NORMAL, "QUANTUM", "Nobody has this skill");

		saga.onSlotUnavailable(new SagaEvents.SlotUnavailable(incident.id(), "No technician with skill QUANTUM has a free slot"));

		assertThat(status(incident)).isEqualTo(IncidentStatus.CANCELLED);
		assertThat(types()).containsExactly(SagaEvents.IncidentCreated.TYPE, SagaEvents.AssignmentCancelled.TYPE);
	}

	@Test
	@DisplayName("an expired hold ends the saga rather than leaving it open")
	void expiredHoldEndsTheSaga() {
		Incident incident = saga.open("acme", Priority.HIGH, "NETWORK", "Printer unreachable");
		UUID slotId = UUID.randomUUID();
		saga.onSlotHeld(new SagaEvents.SlotHeld(incident.id(), slotId, UUID.randomUUID(), "Carla Otxoa",
				Instant.now().plusSeconds(600)));

		saga.onHoldExpired(new SagaEvents.HoldExpired(incident.id(), slotId));

		assertThat(status(incident)).isEqualTo(IncidentStatus.CANCELLED);
		assertThat(detail(incident)).contains("No answer in time");
	}

	@Test
	@DisplayName("a verdict that arrives after the saga ended is ignored, not applied")
	void lateVerdictCannotReopenATerminalSaga() {
		Incident incident = saga.open("acme", Priority.HIGH, "NETWORK", "Wi-Fi flapping");
		UUID slotId = UUID.randomUUID();
		saga.onSlotHeld(new SagaEvents.SlotHeld(incident.id(), slotId, UUID.randomUUID(), "Ane Mendizabal",
				Instant.now().plusSeconds(600)));
		saga.onHoldExpired(new SagaEvents.HoldExpired(incident.id(), slotId));
		int eventsBefore = types().size();

		// The SLA service was slow, not wrong: its answer is simply too late to matter.
		saga.onSlaApproved(new SagaEvents.SlaApproved(incident.id(), 240));

		assertThat(status(incident)).isEqualTo(IncidentStatus.CANCELLED);
		assertThat(types()).hasSize(eventsBefore);
	}

	@Test
	@DisplayName("an event about an incident this service has never seen is dropped")
	void unknownIncidentIsIgnored() {
		saga.onSlaApproved(new SagaEvents.SlaApproved(UUID.randomUUID(), 60));

		assertThat(outbox.findAll()).isEmpty();
	}

	private IncidentStatus status(Incident incident) {
		return incidents.findById(incident.id()).orElseThrow().status();
	}

	private String detail(Incident incident) {
		return incidents.findById(incident.id()).orElseThrow().resolutionDetail();
	}

	/** In the order they were decided, which is the order a consumer will see them. */
	private List<String> types() {
		return outbox.findAll().stream()
				.sorted(java.util.Comparator.comparing(OutboxMessage::createdAt).thenComparing(OutboxMessage::eventType))
				.map(OutboxMessage::eventType)
				.toList();
	}
}
