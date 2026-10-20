package dev.guardia.incidents;

/** Topic names this service reads or writes. Each service keeps its own list. */
public final class Topics {

	public static final String INCIDENT_EVENTS = "incident-events";
	public static final String SCHEDULING_EVENTS = "scheduling-events";
	public static final String SLA_EVENTS = "sla-events";

	private Topics() {
	}
}
