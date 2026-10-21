package dev.guardia.incidents.saga;

import static org.assertj.core.api.Assertions.assertThat;

import dev.guardia.incidents.ServiceTest;
import dev.guardia.incidents.domain.IncidentRepository;
import dev.guardia.incidents.domain.Priority;
import dev.guardia.platform.outbox.OutboxMessage;
import dev.guardia.platform.outbox.OutboxRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two publishers, one outbox.
 *
 * <p>The README claims the poller can be run in more than one replica without two of them
 * sending the same message and without either waiting on the other. That rests entirely on
 * {@code FOR UPDATE SKIP LOCKED}, and a plain {@code FOR UPDATE} would look identical in every
 * single-threaded test while quietly turning the second publisher into a queue behind the
 * first. So this test holds a row locked in one transaction and checks what a second
 * transaction does with it: step over it, and get on with the rest.
 */
class OutboxClaimTest extends ServiceTest {

	@Autowired
	IncidentSaga saga;

	@Autowired
	OutboxRepository outbox;

	@Autowired
	IncidentRepository incidents;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void clean() {
		outbox.deleteAll();
		incidents.deleteAll();
	}

	@Test
	@DisplayName("a row another publisher is holding is stepped over, not waited for")
	void concurrentPublishersDoNotCollide() throws Exception {
		saga.open("acme", Priority.NORMAL, "NETWORK", "First");
		saga.open("acme", Priority.NORMAL, "DATABASE", "Second");
		assertThat(outbox.findAll()).hasSize(2);

		TransactionTemplate transactions = new TransactionTemplate(transactionManager);
		CountDownLatch claimed = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService other = Executors.newSingleThreadExecutor();

		try {
			// One publisher claims a single row and keeps its transaction open.
			var held = other.submit(() -> transactions.execute(status -> {
				UUID first = outbox.claimUnpublished(Limit.of(1)).getFirst().id();
				claimed.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return first;
			}));

			assertThat(claimed.await(10, TimeUnit.SECONDS)).as("the first claim should not hang").isTrue();

			// A second publisher asks for everything available while that row is still locked.
			List<UUID> seen = transactions.execute(status ->
					outbox.claimUnpublished(Limit.of(10)).stream().map(OutboxMessage::id).toList());

			release.countDown();
			UUID lockedRow = held.get(10, TimeUnit.SECONDS);

			assertThat(seen)
					.as("the locked row must be skipped, not duplicated")
					.doesNotContain(lockedRow);
			assertThat(seen)
					.as("and the other row must still be claimed, so nobody is left waiting")
					.hasSize(1);
		} finally {
			release.countDown();
			other.shutdownNow();
		}
	}
}
