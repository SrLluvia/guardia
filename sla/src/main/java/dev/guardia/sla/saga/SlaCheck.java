package dev.guardia.sla.saga;

import dev.guardia.platform.outbox.Outbox;
import dev.guardia.sla.Topics;
import dev.guardia.sla.domain.Contract;
import dev.guardia.sla.domain.ContractRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The step that can say no.
 *
 * <p>It compares the hour scheduling managed to find against what the customer's contract
 * promises for that priority. Nothing here knows about slots or incidents beyond the facts
 * the event carried: this service holds contracts, and that is all.
 */
@Service
public class SlaCheck {

	private static final Logger log = LoggerFactory.getLogger(SlaCheck.class);

	private final ContractRepository contracts;
	private final Outbox outbox;

	public SlaCheck(ContractRepository contracts, Outbox outbox) {
		this.contracts = contracts;
		this.outbox = outbox;
	}

	@Transactional
	public void onSlotHeld(SlaEvents.SlotHeld event) {
		Optional<Contract> contract = contracts.findByCustomerIdAndPriority(event.customerId(), event.priority());
		if (contract.isEmpty()) {
			// Not a fault to retry: no contract is a decision, and a definite no beats a
			// saga left hanging until its hold times out.
			reject(event, "No contract covers %s at priority %s".formatted(event.customerId(), event.priority()));
			return;
		}

		int responseMinutes = contract.get().responseMinutes();
		Instant deadline = event.incidentCreatedAt().plus(Duration.ofMinutes(responseMinutes));

		if (event.startsAt().isAfter(deadline)) {
			long late = Duration.between(deadline, event.startsAt()).toMinutes();
			reject(event, "Earliest slot is %d min past the %d min response time".formatted(late, responseMinutes));
			return;
		}

		log.info("incident {} within its {} min response time", event.incidentId(), responseMinutes);
		outbox.append(event.incidentId(), Topics.SLA_EVENTS, SlaEvents.SlaApproved.TYPE,
				new SlaEvents.SlaApproved(event.incidentId(), responseMinutes));
	}

	private void reject(SlaEvents.SlotHeld event, String reason) {
		log.info("incident {} rejected: {}", event.incidentId(), reason);
		outbox.append(event.incidentId(), Topics.SLA_EVENTS, SlaEvents.SlaRejected.TYPE,
				new SlaEvents.SlaRejected(event.incidentId(), reason));
	}
}
