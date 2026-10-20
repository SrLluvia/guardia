package dev.guardia.sla.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Contract.Key> {

	Optional<Contract> findByCustomerIdAndPriority(String customerId, String priority);
}
