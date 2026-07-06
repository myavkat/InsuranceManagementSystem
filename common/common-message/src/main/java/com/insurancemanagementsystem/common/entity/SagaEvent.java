package com.insurancemanagementsystem.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saga_events", uniqueConstraints = @UniqueConstraint(columnNames = {"saga_id", "event_type"}))
public class SagaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "received_at", updatable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = Instant.now();
    }
}
