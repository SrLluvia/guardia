package dev.guardia.scheduling.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

public final class SchedulingEvents {

	// ---- consumed ----

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record IncidentCreated(
			UUID incidentId,
			String customerId,
			String priority,
			String requiredSkill,
			Instant createdAt) {
		public static final String TYPE = "IncidentCreated";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AssignmentConfirmed(UUID incidentId, UUID slotId) {
		public static final String TYPE = "AssignmentConfirmed";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AssignmentCancelled(UUID incidentId, UUID slotId, String reason) {
		public static final String TYPE = "AssignmentCancelled";
	}

	// ---- published ----

	/**
	 * Carries the incident's own details back out again.
	 *
	 * <p>The SLA service needs the customer, the priority and when the incident was raised in
	 * order to judge the slot. It could keep its own copy of every incident and join locally,
	 * but then it would depend on two topics arriving in the right order, which Kafka only
	 * promises within one partition of one topic. Passing the facts along with the event is
	 * cheaper and has no ordering problem.
	 */
	public record SlotHeld(
			UUID incidentId,
			UUID slotId,
			UUID technicianId,
			String technicianName,
			Instant startsAt,
			String customerId,
			String priority,
			Instant incidentCreatedAt) {
		public static final String TYPE = "SlotHeld";
	}

	public record SlotUnavailable(UUID incidentId, String reason) {
		public static final String TYPE = "SlotUnavailable";
	}

	public record HoldExpired(UUID incidentId, UUID slotId) {
		public static final String TYPE = "HoldExpired";
	}

	private SchedulingEvents() {
	}
}
