package dev.guardia.sla.saga;

import static org.assertj.core.api.Assertions.assertThat;

import dev.guardia.platform.outbox.OutboxMessage;
import dev.guardia.platform.outbox.OutboxRepository;
import dev.guardia.sla.ServiceTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The step that can say no.
 *
 * <p>Contracts come from the seed migration: acme is promised 240 minutes at HIGH priority and
 * 30 at CRITICAL, globex 15 at CRITICAL. The same slot can therefore pass for one customer and
 * fail for another, which is the whole point of the check.
 */
class SlaCheckTest extends ServiceTest {

	@Autowired
	SlaCheck check;

	@Autowired
	OutboxRepository outbox;

	@BeforeEach
	void clean() {
		outbox.deleteAll();
	}

	@Test
	@DisplayName("a slot inside the contracted response time is approved")
	void approvesWhatTheContractAllows() {
		Instant raised = Instant.now();

		check.onSlotHeld(slotHeld("acme", "HIGH", raised, raised.plus(Duration.ofHours(1))));

		assertThat(type()).isEqualTo(SlaEvents.SlaApproved.TYPE);
		assertThat(payload()).contains("240");
	}

	@Test
	@DisplayName("a slot past the response time is rejected, and says by how much")
	void rejectsWhatIsTooLate() {
		Instant raised = Instant.now();

		// globex is promised 15 minutes at CRITICAL; the earliest hour is two hours out.
		check.onSlotHeld(slotHeld("globex", "CRITICAL", raised, raised.plus(Duration.ofHours(2))));

		assertThat(type()).isEqualTo(SlaEvents.SlaRejected.TYPE);
		assertThat(payload()).contains("105 min past the 15 min response time");
	}

	@Test
	@DisplayName("the boundary counts as met, not missed")
	void theDeadlineItselfIsAcceptable() {
		Instant raised = Instant.now();

		check.onSlotHeld(slotHeld("globex", "HIGH", raised, raised.plus(Duration.ofMinutes(60))));

		assertThat(type()).isEqualTo(SlaEvents.SlaApproved.TYPE);
	}

	@Test
	@DisplayName("the same hour passes for one customer and fails for another")
	void theContractIsWhatDecides() {
		Instant raised = Instant.now();
		Instant inTwoHours = raised.plus(Duration.ofHours(2));

		check.onSlotHeld(slotHeld("acme", "HIGH", raised, inTwoHours));
		assertThat(type()).isEqualTo(SlaEvents.SlaApproved.TYPE);

		outbox.deleteAll();
		check.onSlotHeld(slotHeld("globex", "HIGH", raised, inTwoHours));
		assertThat(type()).isEqualTo(SlaEvents.SlaRejected.TYPE);
	}

	@Test
	@DisplayName("an uncovered customer is a definite no, not a retry")
	void noContractIsAnAnswer() {
		Instant raised = Instant.now();

		check.onSlotHeld(slotHeld("initech", "HIGH", raised, raised.plus(Duration.ofMinutes(5))));

		// A thrown exception here would retry, then dead-letter, and leave the saga waiting
		// for a verdict until its hold expired. Saying no is the better answer.
		assertThat(type()).isEqualTo(SlaEvents.SlaRejected.TYPE);
		assertThat(payload()).contains("No contract covers initech");
	}

	private SlaEvents.SlotHeld slotHeld(String customerId, String priority, Instant raised, Instant startsAt) {
		return new SlaEvents.SlotHeld(UUID.randomUUID(), UUID.randomUUID(), startsAt, customerId, priority, raised);
	}

	private String type() {
		return only().eventType();
	}

	private String payload() {
		return only().payload();
	}

	private OutboxMessage only() {
		assertThat(outbox.findAll()).hasSize(1);
		return outbox.findAll().getFirst();
	}
}
