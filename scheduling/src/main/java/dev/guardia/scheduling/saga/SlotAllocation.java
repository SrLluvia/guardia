package dev.guardia.scheduling.saga;

import dev.guardia.scheduling.Topics;
import dev.guardia.scheduling.domain.Slot;
import dev.guardia.scheduling.domain.SlotRepository;
import dev.guardia.scheduling.domain.SlotStatus;
import dev.guardia.platform.outbox.Outbox;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Holds, books and releases slots on behalf of sagas it never sees the whole of. */
@Service
public class SlotAllocation {

	private static final Logger log = LoggerFactory.getLogger(SlotAllocation.class);

	private final SlotRepository slots;
	private final Outbox outbox;
	private final Clock clock;
	private final Duration holdTtl;

	public SlotAllocation(SlotRepository slots, Outbox outbox, Clock clock,
			@Value("${guardia.hold.ttl:PT2M}") Duration holdTtl) {
		this.slots = slots;
		this.outbox = outbox;
		this.clock = clock;
		this.holdTtl = holdTtl;
	}

	@Transactional
	public void onIncidentCreated(SchedulingEvents.IncidentCreated event) {
		Instant now = Instant.now(clock);

		// A slot may already be held for this incident if an earlier delivery got as far as
		// the hold. Answering again with the same fact is correct; taking a second slot is not.
		Optional<Slot> existing = slots.findByIncidentId(event.incidentId());
		if (existing.isPresent()) {
			log.info("incident {} already holds slot {}", event.incidentId(), existing.get().id());
			return;
		}

		List<Slot> candidates = slots.findFirstFree(event.requiredSkill(), now, Limit.of(1));
		if (candidates.isEmpty()) {
			outbox.append(event.incidentId(), Topics.SCHEDULING_EVENTS, SchedulingEvents.SlotUnavailable.TYPE,
					new SchedulingEvents.SlotUnavailable(event.incidentId(),
							"No technician with skill " + event.requiredSkill() + " has a free slot"));
			return;
		}

		Slot slot = candidates.getFirst();
		try {
			slot.holdFor(event.incidentId(), now.plus(holdTtl));
			slots.saveAndFlush(slot);
		} catch (OptimisticLockingFailureException | IllegalStateException e) {
			// Another incident reached this slot first. Rather than loop here, say no and let
			// the saga end; a human retry is a new incident, which is the honest model.
			log.info("slot {} was taken while we were holding it", slot.id());
			outbox.append(event.incidentId(), Topics.SCHEDULING_EVENTS, SchedulingEvents.SlotUnavailable.TYPE,
					new SchedulingEvents.SlotUnavailable(event.incidentId(), "The last free slot was taken first"));
			return;
		}

		outbox.append(event.incidentId(), Topics.SCHEDULING_EVENTS, SchedulingEvents.SlotHeld.TYPE,
				new SchedulingEvents.SlotHeld(
						event.incidentId(),
						slot.id(),
						slot.technician().id(),
						slot.technician().name(),
						slot.startsAt(),
						event.customerId(),
						event.priority(),
						event.createdAt()));
	}

	@Transactional
	public void onAssignmentConfirmed(SchedulingEvents.AssignmentConfirmed event) {
		slots.findByIncidentId(event.incidentId()).ifPresentOrElse(slot -> {
			if (slot.status() == SlotStatus.BOOKED) {
				return; // already done; a repeat delivery
			}
			slot.book();
		}, () -> log.warn("confirmation for incident {} with no slot held here", event.incidentId()));
	}

	/**
	 * The compensation.
	 *
	 * <p>Releasing a slot that is already free is a no-op, not an error: this is reached by a
	 * repeat delivery, and by a cancellation for an incident that never got as far as a hold.
	 */
	@Transactional
	public void onAssignmentCancelled(SchedulingEvents.AssignmentCancelled event) {
		slots.findByIncidentId(event.incidentId()).ifPresent(slot -> {
			if (slot.status() == SlotStatus.BOOKED) {
				// Booked, then cancelled: the appointment existed, so it has to be given up too.
				log.info("releasing booked slot {} after cancellation", slot.id());
			}
			slot.release();
		});
	}

	/**
	 * Lets go of holds nobody came back for.
	 *
	 * <p>Without this a saga whose next step never arrives would keep a technician's hour for
	 * ever. The release is announced, which is what lets the incident reach a terminal state
	 * instead of waiting on a reply that is not coming.
	 */
	@Transactional
	public int expireStaleHolds() {
		Instant now = Instant.now(clock);
		List<Slot> expired = slots.findExpiredHolds(now, Limit.of(50));
		for (Slot slot : expired) {
			UUID incidentId = slot.incidentId();
			log.info("hold on slot {} for incident {} expired", slot.id(), incidentId);
			slot.release();
			outbox.append(incidentId, Topics.SCHEDULING_EVENTS, SchedulingEvents.HoldExpired.TYPE,
					new SchedulingEvents.HoldExpired(incidentId, slot.id()));
		}
		return expired.size();
	}
}
