package dev.guardia.scheduling.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a rolling window of free hours in each technician's diary.
 *
 * <p>The agenda is not seeded by a migration because timestamps written into a migration are
 * wrong the next day, and a demo whose slots are all in the past demonstrates nothing. Slots
 * are topped up from the last one already scheduled, so running this twice adds nothing.
 */
@Component
public class SlotHorizon {

	private static final Logger log = LoggerFactory.getLogger(SlotHorizon.class);
	private static final Duration SLOT_LENGTH = Duration.ofHours(1);

	private final TechnicianRepository technicians;
	private final SlotRepository slots;
	private final Clock clock;
	private final int horizonHours;
	private final Duration leadTime;

	public SlotHorizon(TechnicianRepository technicians, SlotRepository slots, Clock clock,
			@Value("${guardia.agenda.horizon-hours:8}") int horizonHours,
			@Value("${guardia.agenda.lead-time:PT1H}") Duration leadTime) {
		this.technicians = technicians;
		this.slots = slots;
		this.clock = clock;
		this.horizonHours = horizonHours;
		this.leadTime = leadTime;
	}

	/**
	 * A fixed delay fires once at startup and then on its interval, which is exactly the
	 * schedule wanted here. Adding an ApplicationReadyEvent listener as well ran this twice
	 * at once, and two runs that both saw an empty diary each filled it — the duplicate hours
	 * that {@code slot_one_per_technician_hour} now also makes impossible.
	 */
	@Scheduled(fixedDelayString = "${guardia.agenda.top-up-interval:60000}")
	@Transactional
	public void topUp() {
		Instant now = Instant.now(clock);
		// The first hour on offer. The default lead time of an hour is not decoration: with
		// no lead time the earliest slot is the next hour, which at ten to the hour is ten
		// minutes away and at five past is fifty-five — so whether a tight response time can
		// be met would depend on what the clock happened to say. An hour of notice is also
		// what a real field desk has.
		Instant nextHour = now.plus(leadTime).truncatedTo(ChronoUnit.HOURS).plus(SLOT_LENGTH);
		int created = 0;

		for (Technician technician : technicians.findAll()) {
			long free = slots.countByTechnicianAndStatusAndStartsAtAfter(technician, SlotStatus.FREE, now);
			Instant next = slots.findTopByTechnicianOrderByStartsAtDesc(technician)
					.map(last -> last.startsAt().plus(SLOT_LENGTH))
					.filter(candidate -> candidate.isAfter(nextHour))
					.orElse(nextHour);

			for (long i = free; i < horizonHours; i++) {
				slots.save(new Slot(UUID.randomUUID(), technician, next, SLOT_LENGTH));
				next = next.plus(SLOT_LENGTH);
				created++;
			}
		}
		if (created > 0) {
			log.info("added {} free slots to the agenda", created);
		}
	}

	/** Visible for tests that want a diary with nothing in it. */
	public List<Slot> all() {
		return slots.findAll();
	}
}
