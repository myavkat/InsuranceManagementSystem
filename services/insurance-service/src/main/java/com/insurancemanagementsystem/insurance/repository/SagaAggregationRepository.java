package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.SagaAggregation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SagaAggregation}.
 * <p>
 * Provides an atomic find-and-delete via {@link #findByIdForUpdate(UUID)} with a
 * pessimistic write lock, ensuring that aggregation state is consumed exactly once within
 * the enclosing DB transaction.
 */
@Repository
public interface SagaAggregationRepository extends JpaRepository<SagaAggregation, UUID> {

	/**
	 * Finds a saga aggregation row by its primary key, acquiring a
	 * {@code PESSIMISTIC_WRITE} (SELECT FOR UPDATE) lock.
	 * <p>
	 * Must be called within an active transaction. When paired with a subsequent
	 * {@code delete()}, the row is atomically consumed — if the transaction commits the
	 * delete persists; if it rolls back the delete is undone.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SagaAggregation s where s.sagaId = :sagaId")
	Optional<SagaAggregation> findByIdForUpdate(@Param("sagaId") UUID sagaId);

}
