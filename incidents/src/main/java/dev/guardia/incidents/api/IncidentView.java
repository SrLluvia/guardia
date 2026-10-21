package dev.guardia.incidents.api;

import dev.guardia.incidents.domain.Incident;
import java.time.Instant;
import java.util.UUID;

/** What the browser is told. Separate from the entity so the table can change without the API. */
public record IncidentView(
		UUID id,
		String customerId,
		String priority,
		String requiredSkill,
		String summary,
		String status,
		String detail,
		UUID technicianId,
		String technicianName,
		Instant scheduledStart,
		Instant createdAt) {

	public static IncidentView of(Incident incident) {
		return new IncidentView(
				incident.id(),
				incident.customerId(),
				incident.priority().name(),
				incident.requiredSkill(),
				incident.summary(),
				incident.status().name(),
				incident.resolutionDetail(),
				incident.technicianId(),
				incident.technicianName(),
				incident.scheduledStart(),
				incident.createdAt());
	}
}
