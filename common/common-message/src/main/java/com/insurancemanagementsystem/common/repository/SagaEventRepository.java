package com.insurancemanagementsystem.common.repository;

import com.insurancemanagementsystem.common.entity.SagaEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaEventRepository extends JpaRepository<SagaEvent, UUID> {
    boolean existsBySagaIdAndEventType(UUID sagaId, String eventType);
    Optional<SagaEvent> findBySagaIdAndEventType(UUID sagaId, String eventType);

    /**
     * Atomically inserts a dedup marker.
     * <p>
     * Uses {@code saveAndFlush} so the INSERT executes immediately and the
     * {@link DataIntegrityViolationException} is caught inside this method's
     * try-catch even when called within an existing transaction (e.g. inside
     * {@code TransactionTemplate.executeWithoutResult}).  Using plain {@code save}
     * would only queue the insert until flush/commit time, letting the exception
     * escape this method and silently corrupt the surrounding transaction.
     *
     * @return true if this event was already processed (duplicate), false if new.
     */
    default boolean tryInsertDedup(UUID sagaId, String eventType) {
        SagaEvent dedup = SagaEvent.builder()
                .sagaId(sagaId)
                .eventType(eventType)
                .build();
        try {
            saveAndFlush(dedup);
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }
}
