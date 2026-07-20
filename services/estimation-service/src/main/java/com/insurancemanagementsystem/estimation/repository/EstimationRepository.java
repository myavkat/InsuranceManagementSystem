package com.insurancemanagementsystem.estimation.repository;

import com.insurancemanagementsystem.estimation.entity.Estimation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstimationRepository extends JpaRepository<Estimation, UUID> {

	Optional<Estimation> findBySagaId(UUID sagaId);

	List<Estimation> findByCustomerId(UUID customerId);

	List<Estimation> findByStatus(Estimation.Status status);

	List<Estimation> findByStatusAndCreatedAtBefore(Estimation.Status status, Instant createdAt);

	List<Estimation> findByCustomerIdAndStatus(UUID customerId, Estimation.Status status);

	List<Estimation> findByCreatedAtBetween(Instant from, Instant to);

	List<Estimation> findByStatusIn(List<Estimation.Status> statuses, Sort sort);

	Page<Estimation> findByCustomerId(UUID customerId, Pageable pageable);

	Page<Estimation> findByStatus(Estimation.Status status, Pageable pageable);

	Page<Estimation> findByCustomerIdAndStatus(UUID customerId, Estimation.Status status, Pageable pageable);

	long countByStatus(Estimation.Status status);

	@Query("SELECT COALESCE(SUM(e.premium), 0) FROM Estimation e WHERE e.status = 'ACTIVE'")
	BigDecimal sumPremiumByActiveStatus();

	@Query("SELECT e.status, COUNT(e) FROM Estimation e GROUP BY e.status")
	List<Object[]> countGroupedByStatus();

	@Query("SELECT COUNT(DISTINCT e.customerId) FROM Estimation e WHERE e.createdAt >= :since")
	long countDistinctCustomerIdsSince(@Param("since") Instant since);

}
