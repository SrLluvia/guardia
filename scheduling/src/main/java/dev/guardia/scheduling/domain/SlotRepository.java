package dev.guardia.scheduling.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

	/** The earliest free slot a technician with the right skill can offer. */
	@Query("""
			select s from Slot s
			where s.status = dev.guardia.scheduling.domain.SlotStatus.FREE
			  and s.technician.skill = :skill
			  and s.startsAt > :after
			order by s.startsAt asc
			""")
	List<Slot> findFirstFree(@Param("skill") String skill, @Param("after") Instant after, Limit limit);

	Optional<Slot> findByIncidentId(UUID incidentId);

	@Query("""
			select s from Slot s
			where s.status = dev.guardia.scheduling.domain.SlotStatus.HELD
			  and s.holdExpiresAt < :now
			""")
	List<Slot> findExpiredHolds(@Param("now") Instant now, Limit limit);

	/** Free hours still to come. Held and booked ones do not count: they are spoken for. */
	long countByTechnicianAndStatusAndStartsAtAfter(Technician technician, SlotStatus status, Instant after);

	Optional<Slot> findTopByTechnicianOrderByStartsAtDesc(Technician technician);
}
