package dev.guardia.incidents.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

	List<Incident> findAllByOrderByCreatedAtDesc(Limit limit);
}
