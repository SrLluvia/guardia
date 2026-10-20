package dev.guardia.incidents.domain;

/**
 * The saga's state, as seen from the service that owns the outcome.
 *
 * <pre>
 *   RECEIVED ──slot held──▶ SLOT_HELD ──SLA approved──▶ ASSIGNED
 *       │                       │
 *       │ no technician         │ SLA rejected, or hold expired
 *       ▼                       ▼
 *            C A N C E L L E D
 * </pre>
 *
 * ASSIGNED and CANCELLED are terminal: an event that arrives afterwards is late, not new,
 * and is ignored rather than allowed to move a finished saga.
 */
public enum IncidentStatus {
	RECEIVED, SLOT_HELD, ASSIGNED, CANCELLED;

	public boolean isTerminal() {
		return this == ASSIGNED || this == CANCELLED;
	}
}
