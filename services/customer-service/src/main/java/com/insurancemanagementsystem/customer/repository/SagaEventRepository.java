package com.insurancemanagementsystem.customer.repository;

import com.insurancemanagementsystem.customer.entity.SagaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaEventRepository extends JpaRepository<SagaEvent, UUID> {
    boolean existsBySagaIdAndEventType(UUID sagaId, String eventType);
    Optional<SagaEvent> findBySagaIdAndEventType(UUID sagaId, String eventType);
}
