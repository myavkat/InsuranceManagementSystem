package com.insurancemanagementsystem.common.repository;

import com.insurancemanagementsystem.common.entity.SagaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaEventRepository extends JpaRepository<SagaEvent, UUID> {

	boolean existsBySagaIdAndEventType(UUID sagaId, String eventType);

	Optional<SagaEvent> findBySagaIdAndEventType(UUID sagaId, String eventType);

	/**
	 * Atomically insert a dedup marker using a native INSERT … ON CONFLICT DO NOTHING.
	 * <p>
	 * Returns the number of rows inserted: 1 if this is a new (sagaId, eventType) pair, 0
	 * if a row already exists (duplicate). The operation is atomic at the database level
	 * — no TOCTOU race — and does <b>not</b> create a managed JPA entity, so the
	 * persistence context stays clean even when the caller is inside a
	 * {@code TransactionTemplate} whose commit would otherwise re-flush a poisoned
	 * {@link SagaEvent}.
	 * @param sagaId saga identifier
	 * @param eventType event type constant from {@code EventConstants}
	 * @return 1 if the row was inserted (new event), 0 if it already existed (duplicate)
	 * @throws org.springframework.transaction.IllegalTransactionStateException if called
	 * without an active transaction — this method must run inside the same transaction as
	 * any subsequent state changes (e.g. saving an outbox event), otherwise dedup-marking
	 * and the actual side effect can commit independently and diverge.
	 */
	@Modifying
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
			INSERT INTO saga_events (id, saga_id, event_type, received_at)
			VALUES (gen_random_uuid(), :sagaId, :eventType, CURRENT_TIMESTAMP)
			ON CONFLICT (saga_id, event_type) DO NOTHING
			""", nativeQuery = true)
	int insertDedupMarker(@Param("sagaId") UUID sagaId, @Param("eventType") String eventType);

	/**
	 * Atomically inserts a dedup marker and reports whether the event was already
	 * processed.
	 * <p>
	 * Uses {@link #insertDedupMarker(UUID, String)} — a native
	 * {@code INSERT … ON CONFLICT DO NOTHING} — which is fully atomic at the DB level and
	 * does not materialise a managed JPA entity. This avoids the persistence-context
	 * poisoning that occurred when the previous implementation used {@code saveAndFlush}
	 * + {@code DataIntegrityViolationException} catch (Hibernate would re-flush the
	 * failed insert at commit time, corrupting the surrounding transaction).
	 * @return {@code true} if this event was already processed (duplicate), {@code false}
	 * if new.
	 */
	default boolean tryInsertDedup(UUID sagaId, String eventType) {
		return insertDedupMarker(sagaId, eventType) == 0;
	}

	/**
	 * Delete dedup markers older than the specified cutoff. SAGA workflows complete
	 * within minutes; dedup markers older than the retention period are no longer needed
	 * for idempotency.
	 * @param cutoff delete rows with received_at before this time
	 * @return number of deleted rows
	 */
	@Modifying
	@Query(value = "DELETE FROM saga_events WHERE received_at < :cutoff", nativeQuery = true)
	int deleteByReceivedAtBefore(@Param("cutoff") Instant cutoff);

}
