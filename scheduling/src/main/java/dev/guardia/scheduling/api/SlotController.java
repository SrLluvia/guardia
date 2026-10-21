package dev.guardia.scheduling.api;

import dev.guardia.scheduling.domain.Slot;
import dev.guardia.scheduling.domain.SlotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The diary, readable.
 *
 * <p>This service's work arrives over Kafka and it needs no HTTP to do its job. What it is for
 * is the page: an incident's text can say an hour was released, but watching the hour itself go
 * from held back to free is the only way to see the compensation rather than be told about it.
 *
 * <p>Read-only on purpose. Hours are taken and released by the saga, never by a caller, and
 * there is no endpoint here that would let anyone do otherwise.
 */
@RestController
@RequestMapping("/api/slots")
public class SlotController {

	private static final int MAX_SLOTS = 200;

	private final SlotRepository slots;
	private final Clock clock;

	public SlotController(SlotRepository slots, Clock clock) {
		this.slots = slots;
		this.clock = clock;
	}

	/** The hours still to come, a technician at a time and in order within each. */
	@GetMapping
	public List<SlotView> upcoming() {
		return slots.findByStartsAtAfterOrderByStartsAtAsc(Instant.now(clock), Limit.of(MAX_SLOTS)).stream()
				.sorted(Comparator.comparing((Slot slot) -> slot.technician().name()).thenComparing(Slot::startsAt))
				.map(SlotView::of)
				.toList();
	}
}
