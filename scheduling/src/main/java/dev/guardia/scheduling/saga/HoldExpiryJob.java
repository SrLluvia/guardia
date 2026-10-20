package dev.guardia.scheduling.saga;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the timeout that makes every saga finite.
 *
 * <p>Separate from the work itself so a test can call {@link SlotAllocation#expireStaleHolds()}
 * directly instead of waiting for a clock.
 */
@Component
public class HoldExpiryJob {

	private final SlotAllocation allocation;

	public HoldExpiryJob(SlotAllocation allocation) {
		this.allocation = allocation;
	}

	@Scheduled(fixedDelayString = "${guardia.hold.sweep-interval:5000}")
	public void sweep() {
		allocation.expireStaleHolds();
	}
}
