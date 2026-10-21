package dev.guardia.incidents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident {

	@Id
	private UUID id;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private String customerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Priority priority;

	@Column(name = "required_skill", nullable = false, updatable = false)
	private String requiredSkill;

	@Column(nullable = false, updatable = false)
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IncidentStatus status;

	@Column(name = "technician_id")
	private UUID technicianId;

	@Column(name = "technician_name")
	private String technicianName;

	@Column(name = "slot_id")
	private UUID slotId;

	@Column(name = "scheduled_start")
	private Instant scheduledStart;

	/** Why it ended the way it did, in words a human can read off the screen. */
	@Column(name = "resolution_detail")
	private String resolutionDetail;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Incident() {
	}

	public Incident(UUID id, String customerId, Priority priority, String requiredSkill, String summary, Instant now) {
		this.id = id;
		this.customerId = customerId;
		this.priority = priority;
		this.requiredSkill = requiredSkill;
		this.summary = summary;
		this.status = IncidentStatus.RECEIVED;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void holdTaken(UUID technicianId, UUID slotId, Instant scheduledStart, String technicianName, Instant now) {
		this.technicianId = technicianId;
		this.technicianName = technicianName;
		this.slotId = slotId;
		this.scheduledStart = scheduledStart;
		this.resolutionDetail = "Slot held with " + technicianName + ", awaiting SLA check";
		moveTo(IncidentStatus.SLOT_HELD, now);
	}

	public void assigned(Instant now) {
		this.resolutionDetail = "Assigned to " + technicianName + ", within the contracted response time";
		moveTo(IncidentStatus.ASSIGNED, now);
	}

	public void cancelled(String reason, Instant now) {
		this.resolutionDetail = reason;
		moveTo(IncidentStatus.CANCELLED, now);
	}

	private void moveTo(IncidentStatus next, Instant now) {
		this.status = next;
		this.updatedAt = now;
	}

	public UUID id() {
		return id;
	}

	public String customerId() {
		return customerId;
	}

	public Priority priority() {
		return priority;
	}

	public String requiredSkill() {
		return requiredSkill;
	}

	public String summary() {
		return summary;
	}

	public IncidentStatus status() {
		return status;
	}

	public UUID technicianId() {
		return technicianId;
	}

	public String technicianName() {
		return technicianName;
	}

	public UUID slotId() {
		return slotId;
	}

	public Instant scheduledStart() {
		return scheduledStart;
	}

	public String resolutionDetail() {
		return resolutionDetail;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
