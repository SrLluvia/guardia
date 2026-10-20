package dev.guardia.scheduling.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

	List<Technician> findAllBySkill(String skill);
}
