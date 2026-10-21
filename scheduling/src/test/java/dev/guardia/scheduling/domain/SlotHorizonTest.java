package dev.guardia.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.guardia.scheduling.ServiceTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The diary has to keep extending.
 *
 * <p>A top-up that counted every upcoming hour rather than the free ones would fill the diary
 * once and leave it full: every incident after the eighth would be refused for want of a slot,
 * which is a true answer to a question nobody asked. The desk is supposed to have next week.
 */
class SlotHorizonTest extends ServiceTest {

	private static final int EXPECTED_FREE_HOURS = 8;

	@Autowired
	SlotHorizon horizon;

	@Autowired
	SlotRepository slots;

	@Autowired
	TechnicianRepository technicians;

	@BeforeEach
	void clean() {
		slots.deleteAll();
	}

	@Test
	@DisplayName("every technician starts with free hours ahead of them")
	void fillsAnEmptyDiary() {
		horizon.topUp();

		for (Technician technician : technicians.findAll()) {
			assertThat(freeHoursOf(technician)).isEqualTo(EXPECTED_FREE_HOURS);
		}
	}

	@Test
	@DisplayName("running it again changes nothing")
	void doesNotPileUp() {
		horizon.topUp();
		long after = slots.count();

		horizon.topUp();

		assertThat(slots.count()).isEqualTo(after);
	}

	@Test
	@DisplayName("hours that get taken are replaced by later ones")
	void refillsWhatWasTaken() {
		horizon.topUp();
		Technician technician = technicians.findAllBySkill("DATABASE").getFirst();
		Instant lastBefore = latestHourOf(technician);

		// Every free hour of theirs is spoken for, which is what a busy morning looks like.
		List<Slot> taken = slots.findAll().stream()
				.filter(slot -> slot.technician().id().equals(technician.id()))
				.toList();
		taken.forEach(slot -> slot.holdFor(UUID.randomUUID(), Instant.now().plusSeconds(600)));
		slots.saveAll(taken);
		assertThat(freeHoursOf(technician)).isZero();

		horizon.topUp();

		assertThat(freeHoursOf(technician))
				.as("a full diary must grow, not stay full")
				.isEqualTo(EXPECTED_FREE_HOURS);
		assertThat(latestHourOf(technician))
				.as("and the new hours come after the ones already in it")
				.isAfter(lastBefore);
	}

	private long freeHoursOf(Technician technician) {
		return slots.countByTechnicianAndStatusAndStartsAtAfter(technician, SlotStatus.FREE, Instant.now());
	}

	private Instant latestHourOf(Technician technician) {
		return slots.findTopByTechnicianOrderByStartsAtDesc(technician).orElseThrow().startsAt();
	}
}
