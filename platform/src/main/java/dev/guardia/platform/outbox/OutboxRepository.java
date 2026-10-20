package dev.guardia.platform.outbox;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

	/**
	 * Claims a batch of unpublished messages.
	 *
	 * <p>{@code FOR UPDATE SKIP LOCKED} is what makes more than one publisher safe: a row
	 * being handled by another instance is stepped over rather than waited for, so two
	 * replicas never send the same message and neither of them blocks.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("select m from OutboxMessage m where m.publishedAt is null order by m.createdAt")
	List<OutboxMessage> claimUnpublished(Limit limit);
}
