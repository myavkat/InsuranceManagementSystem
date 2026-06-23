package com.insurancemanagementsystem.estimation.repository;

import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);

    List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxEvent.Status status, Instant cutoff);
}
