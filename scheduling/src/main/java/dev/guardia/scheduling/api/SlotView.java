package dev.guardia.scheduling.api;

import dev.guardia.scheduling.domain.Slot;
import java.time.Instant;
import java.util.UUID;

/**
 * One hour of one technician's day, as the outside world sees it.
 *
 * <p>Separate from the entity so the table can change without the API changing with it, and so
 * that nothing internal — the optimistic lock version, the hold's deadline — leaks out.
 */
public record SlotView(
		UUID id,
		UUID technicianId,
		String technicianName,
		String skill,
		Instant startsAt,
		String status,
		UUID incidentId) {

	public static SlotView of(Slot slot) {
		return new SlotView(
				slot.id(),
				slot.technician().id(),
				slot.technician().name(),
				slot.technician().skill(),
				slot.startsAt(),
				slot.status().name(),
				slot.incidentId());
	}
}
