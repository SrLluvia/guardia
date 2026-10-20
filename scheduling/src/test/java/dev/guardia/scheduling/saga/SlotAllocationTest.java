package dev.guardia.scheduling.saga;

import static org.assertj.core.api.Assertions.assertThat;

import dev.guardia.platform.outbox.OutboxMessage;
import dev.guardia.platform.outbox.OutboxRepository;
import dev.guardia.scheduling.ServiceTest;
import dev.guardia.scheduling.domain.Slot;
import dev.guardia.scheduling.domain.SlotHorizon;
import dev.guardia.scheduling.domain.SlotRepository;
import dev.guardia.scheduling.domain.SlotStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * What happens to a technician's hour as a saga passes through it.
 *
 * <p>The hold lifetime is zero here so the timeout can be tested by calling the sweep rather
 * than by waiting for a clock.
 */
@TestPropertySource(properties = "guardia.hold.ttl=PT0S")
class SlotAllocationTest extends ServiceTest {

	@Autowired
	SlotAllocation allocation;

	@Autowired
	SlotRepository slots;

	@Autowired
	SlotHorizon horizon;

	@Autowired
	OutboxRepository outbox;

	@BeforeEach
	void clean() {
		outbox.deleteAll();
		slots.deleteAll();
		horizon.topUp();
	}

	@Test
	@DisplayName("the earliest free slot of a technician with the right skill is held")
	void holdsTheEarliestMatchingSlot() {
		UUID incidentId = UUID.randomUUID();

		allocation.onIncidentCreated(created(incidentId, "DATABASE"));

		Slot held = slots.findByIncidentId(incidentId).orElseThrow();
		assertThat(held.status()).isEqualTo(SlotStatus.HELD);
		assertThat(held.technician().skill()).isEqualTo("DATABASE");
		assertThat(held.startsAt()).isEqualTo(earliestOf("DATABASE"));
		assertThat(types()).containsExactly(SchedulingEvents.SlotHeld.TYPE);
	}

	@Test
	@DisplayName("a skill nobody has is answered, not left hanging")
	void saysSoWhenNobodyCanHelp() {
		UUID incidentId = UUID.randomUUID();

		allocation.onIncidentCreated(created(incidentId, "QUANTUM"));

		assertThat(slots.findByIncidentId(incidentId)).isEmpty();
		assertThat(types()).containsExactly(SchedulingEvents.SlotUnavailable.TYPE);
	}

	@Test
	@DisplayName("two incidents never get the same hour")
	void oneSlotPerIncident() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		allocation.onIncidentCreated(created(first, "DATABASE"));
		allocation.onIncidentCreated(created(second, "DATABASE"));

		Slot a = slots.findByIncidentId(first).orElseThrow();
		Slot b = slots.findByIncidentId(second).orElseThrow();
		assertThat(a.id()).isNotEqualTo(b.id());
	}

	@Test
	@DisplayName("a repeated delivery does not take a second slot")
	void repeatedDeliveryHoldsNothingNew() {
		UUID incidentId = UUID.randomUUID();
		SchedulingEvents.IncidentCreated event = created(incidentId, "DATABASE");

		allocation.onIncidentCreated(event);
		allocation.onIncidentCreated(event);

		assertThat(slots.findAll()).filteredOn(slot -> incidentId.equals(slot.incidentId())).hasSize(1);
	}

	@Test
	@DisplayName("a confirmed assignment books the held slot")
	void confirmationBooksTheSlot() {
		UUID incidentId = UUID.randomUUID();
		allocation.onIncidentCreated(created(incidentId, "HARDWARE"));
		UUID slotId = slots.findByIncidentId(incidentId).orElseThrow().id();

		allocation.onAssignmentConfirmed(new SchedulingEvents.AssignmentConfirmed(incidentId, slotId));

		assertThat(slots.findById(slotId).orElseThrow().status()).isEqualTo(SlotStatus.BOOKED);
	}

	@Test
	@DisplayName("a cancellation gives the hour back, and it can be held again")
	void cancellationCompensates() {
		UUID first = UUID.randomUUID();
		allocation.onIncidentCreated(created(first, "HARDWARE"));
		Slot held = slots.findByIncidentId(first).orElseThrow();
		Instant freedHour = held.startsAt();

		allocation.onAssignmentCancelled(new SchedulingEvents.AssignmentCancelled(first, held.id(), "SLA cannot be met"));

		Slot released = slots.findById(held.id()).orElseThrow();
		assertThat(released.status()).isEqualTo(SlotStatus.FREE);
		assertThat(released.incidentId()).isNull();

		// The compensation is only real if the hour is genuinely on offer again.
		UUID second = UUID.randomUUID();
		allocation.onIncidentCreated(created(second, "HARDWARE"));
		assertThat(slots.findByIncidentId(second).orElseThrow().startsAt()).isEqualTo(freedHour);
	}

	@Test
	@DisplayName("cancelling an incident that never held anything is harmless")
	void cancellingWithoutAHoldIsANoOp() {
		UUID incidentId = UUID.randomUUID();

		allocation.onAssignmentCancelled(new SchedulingEvents.AssignmentCancelled(incidentId, null, "No technician"));

		assertThat(slots.findAll()).allMatch(slot -> slot.status() == SlotStatus.FREE);
	}

	@Test
	@DisplayName("a hold nobody came back for is released, and the release is announced")
	void staleHoldsAreSweptUp() {
		UUID incidentId = UUID.randomUUID();
		allocation.onIncidentCreated(created(incidentId, "DATABASE"));
		UUID slotId = slots.findByIncidentId(incidentId).orElseThrow().id();
		outbox.deleteAll();

		int expired = allocation.expireStaleHolds();

		assertThat(expired).isEqualTo(1);
		assertThat(slots.findById(slotId).orElseThrow().status()).isEqualTo(SlotStatus.FREE);
		// Announced, because the incident cannot reach a terminal state without being told.
		assertThat(types()).containsExactly(SchedulingEvents.HoldExpired.TYPE);
		assertThat(outbox.findAll())
				.singleElement()
				.satisfies(message -> assertThat(message.payload()).contains(incidentId.toString()));
	}

	@Test
	@DisplayName("a booked slot is not swept away by the timeout")
	void bookedSlotsSurviveTheSweep() {
		UUID incidentId = UUID.randomUUID();
		allocation.onIncidentCreated(created(incidentId, "DATABASE"));
		UUID slotId = slots.findByIncidentId(incidentId).orElseThrow().id();
		allocation.onAssignmentConfirmed(new SchedulingEvents.AssignmentConfirmed(incidentId, slotId));

		assertThat(allocation.expireStaleHolds()).isZero();
		assertThat(slots.findById(slotId).orElseThrow().status()).isEqualTo(SlotStatus.BOOKED);
	}

	private SchedulingEvents.IncidentCreated created(UUID incidentId, String skill) {
		return new SchedulingEvents.IncidentCreated(incidentId, "acme", "HIGH", skill, Instant.now());
	}

	private Instant earliestOf(String skill) {
		return slots.findAll().stream()
				.filter(slot -> slot.technician().skill().equals(skill))
				.map(Slot::startsAt)
				.min(Instant::compareTo)
				.orElseThrow();
	}

	private List<String> types() {
		return outbox.findAll().stream()
				.sorted(Comparator.comparing(OutboxMessage::createdAt))
				.map(OutboxMessage::eventType)
				.toList();
	}
}
