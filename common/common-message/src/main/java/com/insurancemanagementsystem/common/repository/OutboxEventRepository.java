package com.insurancemanagementsystem.common.repository;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT * FROM outbox_events WHERE status = :status ORDER BY created_at ASC LIMIT 10 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(@Param("status") OutboxEvent.Status status);

    List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxEvent.Status status, Instant cutoff);
}
