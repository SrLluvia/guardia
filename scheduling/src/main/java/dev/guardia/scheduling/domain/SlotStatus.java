package dev.guardia.scheduling.domain;

public enum SlotStatus {
	/** Nobody's. */
	FREE,
	/** Set aside for one incident, until a deadline. This is the state compensation undoes. */
	HELD,
	/** Confirmed. The saga that asked for it completed. */
	BOOKED
}
