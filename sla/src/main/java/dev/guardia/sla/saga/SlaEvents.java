package dev.guardia.sla.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

public final class SlaEvents {

	// ---- consumed ----

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SlotHeld(
			UUID incidentId,
			UUID slotId,
			Instant startsAt,
			String customerId,
			String priority,
			Instant incidentCreatedAt) {
		public static final String TYPE = "SlotHeld";
	}

	// ---- published ----

	public record SlaApproved(UUID incidentId, int responseMinutes) {
		public static final String TYPE = "SlaApproved";
	}

	public record SlaRejected(UUID incidentId, String reason) {
		public static final String TYPE = "SlaRejected";
	}

	private SlaEvents() {
	}
}
