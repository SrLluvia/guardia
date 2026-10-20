package dev.guardia.incidents.saga;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This service's view of the conversation.
 *
 * <p>The outgoing records are the contract it promises. The incoming ones name only the
 * fields it actually reads and ignore anything else, so the services that produce them can
 * grow their payloads without a coordinated release.
 */
public final class SagaEvents {

	// ---- published by this service ----

	public record IncidentCreated(
			UUID incidentId,
			String customerId,
			String priority,
			String requiredSkill,
			String summary,
			Instant createdAt) {
		public static final String TYPE = "IncidentCreated";
	}

	public record AssignmentConfirmed(UUID incidentId, UUID slotId) {
		public static final String TYPE = "AssignmentConfirmed";
	}

	/** The compensation trigger: whoever is holding something for this incident must let it go. */
	public record AssignmentCancelled(UUID incidentId, UUID slotId, String reason) {
		public static final String TYPE = "AssignmentCancelled";
	}

	// ---- consumed by this service ----

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SlotHeld(UUID incidentId, UUID slotId, UUID technicianId, String technicianName, Instant startsAt) {
		public static final String TYPE = "SlotHeld";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SlotUnavailable(UUID incidentId, String reason) {
		public static final String TYPE = "SlotUnavailable";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record HoldExpired(UUID incidentId, UUID slotId) {
		public static final String TYPE = "HoldExpired";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SlaApproved(UUID incidentId, int responseMinutes) {
		public static final String TYPE = "SlaApproved";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SlaRejected(UUID incidentId, String reason) {
		public static final String TYPE = "SlaRejected";
	}

	private SagaEvents() {
	}
}
