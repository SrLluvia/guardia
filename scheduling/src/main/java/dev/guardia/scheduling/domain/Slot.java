package dev.guardia.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An hour of one technician's day.
 *
 * <p>Holding is not booking. A hold is provisional and carries its own deadline, so the
 * resource is never locked away by a saga that stopped halfway.
 */
@Entity
@Table(name = "slot")
public class Slot {

	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "technician_id", nullable = false, updatable = false)
	private Technician technician;

	@Column(name = "starts_at", nullable = false, updatable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false, updatable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SlotStatus status;

	@Column(name = "incident_id")
	private UUID incidentId;

	@Column(name = "hold_expires_at")
	private Instant holdExpiresAt;

	/**
	 * Optimistic locking is what stops two incidents being handed the same slot: the second
	 * write fails rather than overwriting the first, and its saga is told no slot was available.
	 */
	@Version
	private long version;

	protected Slot() {
	}

	public Slot(UUID id, Technician technician, Instant startsAt, Duration length) {
		this.id = id;
		this.technician = technician;
		this.startsAt = startsAt;
		this.endsAt = startsAt.plus(length);
		this.status = SlotStatus.FREE;
	}

	public void holdFor(UUID incidentId, Instant expiresAt) {
		if (status != SlotStatus.FREE) {
			throw new IllegalStateException("slot " + id + " is " + status);
		}
		this.status = SlotStatus.HELD;
		this.incidentId = incidentId;
		this.holdExpiresAt = expiresAt;
	}

	public void book() {
		if (status != SlotStatus.HELD) {
			throw new IllegalStateException("slot " + id + " is " + status + ", not held");
		}
		this.status = SlotStatus.BOOKED;
		this.holdExpiresAt = null;
	}

	/** The compensating action: everything the hold wrote is undone, and the slot is on offer again. */
	public void release() {
		this.status = SlotStatus.FREE;
		this.incidentId = null;
		this.holdExpiresAt = null;
	}

	public UUID id() {
		return id;
	}

	public Technician technician() {
		return technician;
	}

	public Instant startsAt() {
		return startsAt;
	}

	public SlotStatus status() {
		return status;
	}

	public UUID incidentId() {
		return incidentId;
	}
}
