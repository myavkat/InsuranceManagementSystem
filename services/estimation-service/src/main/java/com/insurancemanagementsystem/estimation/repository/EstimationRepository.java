package com.insurancemanagementsystem.estimation.repository;

import com.insurancemanagementsystem.estimation.entity.Estimation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    Page<Estimation> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Estimation> findByStatus(Estimation.Status status, Pageable pageable);
    Page<Estimation> findByCustomerIdAndStatus(UUID customerId, Estimation.Status status, Pageable pageable);
}
