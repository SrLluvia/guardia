package dev.guardia.incidents.saga;

import dev.guardia.incidents.Topics;
import dev.guardia.incidents.domain.Incident;
import dev.guardia.incidents.domain.IncidentRepository;
import dev.guardia.incidents.domain.Priority;
import dev.guardia.platform.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place an incident's outcome is decided.
 *
 * <p>Every method here runs in one transaction that both changes the incident and appends the
 * event announcing the change, so the two can never disagree. Nothing in this class talks to
 * Kafka directly.
 */
@Service
public class IncidentSaga {

	private static final Logger log = LoggerFactory.getLogger(IncidentSaga.class);

	private final IncidentRepository incidents;
	private final Outbox outbox;
	private final ApplicationEventPublisher localEvents;
	private final Clock clock;

	public IncidentSaga(IncidentRepository incidents, Outbox outbox, ApplicationEventPublisher localEvents, Clock clock) {
		this.incidents = incidents;
		this.outbox = outbox;
		this.localEvents = localEvents;
		this.clock = clock;
	}

	@Transactional
	public Incident open(String customerId, Priority priority, String requiredSkill, String summary) {
		Instant now = Instant.now(clock);
		Incident incident = incidents.save(
				new Incident(UUID.randomUUID(), customerId, priority, requiredSkill, summary, now));

		outbox.append(incident.id(), Topics.INCIDENT_EVENTS, SagaEvents.IncidentCreated.TYPE,
				new SagaEvents.IncidentCreated(incident.id(), customerId, priority.name(), requiredSkill, summary, now));

		changed(incident);
		return incident;
	}

	@Transactional
	public void onSlotHeld(SagaEvents.SlotHeld event) {
		advance(event.incidentId(), incident -> incident.holdTaken(
				event.technicianId(), event.slotId(), event.startsAt(), event.technicianName(), Instant.now(clock)));
	}

	@Transactional
	public void onSlaApproved(SagaEvents.SlaApproved event) {
		advance(event.incidentId(), incident -> {
			incident.assigned(Instant.now(clock));
			outbox.append(incident.id(), Topics.INCIDENT_EVENTS, SagaEvents.AssignmentConfirmed.TYPE,
					new SagaEvents.AssignmentConfirmed(incident.id(), incident.slotId()));
		});
	}

	@Transactional
	public void onSlaRejected(SagaEvents.SlaRejected event) {
		cancel(event.incidentId(), event.reason());
	}

	@Transactional
	public void onSlotUnavailable(SagaEvents.SlotUnavailable event) {
		cancel(event.incidentId(), event.reason());
	}

	/**
	 * The safety net. If a downstream service never answers, the hold times out there and
	 * says so here, which is what guarantees that no saga stays open forever.
	 */
	@Transactional
	public void onHoldExpired(SagaEvents.HoldExpired event) {
		cancel(event.incidentId(), "No answer in time; the held slot was released");
	}

	private void cancel(UUID incidentId, String reason) {
		advance(incidentId, incident -> {
			UUID slotId = incident.slotId();
			incident.cancelled(reason, Instant.now(clock));
			// Announced even when nothing was held: the subscriber decides whether it has
			// anything to undo, which keeps this service ignorant of the other's bookkeeping.
			outbox.append(incident.id(), Topics.INCIDENT_EVENTS, SagaEvents.AssignmentCancelled.TYPE,
					new SagaEvents.AssignmentCancelled(incident.id(), slotId, reason));
		});
	}

	private void advance(UUID incidentId, java.util.function.Consumer<Incident> step) {
		Optional<Incident> found = incidents.findById(incidentId);
		if (found.isEmpty()) {
			log.warn("event for unknown incident {}, ignoring", incidentId);
			return;
		}
		Incident incident = found.get();
		if (incident.status().isTerminal()) {
			// A late event, not a new one. Reached when a slow reply overtakes a timeout.
			log.info("incident {} already {}, ignoring late event", incidentId, incident.status());
			return;
		}
		step.accept(incident);
		changed(incident);
	}

	private void changed(Incident incident) {
		localEvents.publishEvent(new IncidentChanged(incident.id()));
	}

	/** In-process notification, used to push the new state to connected browsers. */
	public record IncidentChanged(UUID incidentId) {
	}
}
