package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent saga aggregation store — replaces the in-memory {@code ConcurrentHashMap} in
 * {@link com.insurancemanagementsystem.insurance.config.SagaAggregationStore}.
 * <p>
 * Each row correlates the payloads of up to three independent events
 * ({@code ESTIMATION_REQUESTED}, {@code CUSTOMER_VALIDATED}, {@code VEHICLE_VALIDATED})
 * needed for premium calculation. The row is consumed atomically (SELECT FOR UPDATE +
 * DELETE) within the same DB transaction as the outbox event save, so a rollback
 * preserves the aggregation state for retry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saga_aggregations")
public class SagaAggregation {

	@Id
	@Column(name = "saga_id")
	private UUID sagaId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "estimation_request_payload")
	private String estimationRequestPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "customer_validated_payload")
	private String customerValidatedPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "vehicle_validated_payload")
	private String vehicleValidatedPayload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = Instant.now();
	}

	/**
	 * Returns {@code true} when all three event payloads have been stored and the saga is
	 * ready for premium calculation.
	 */
	public boolean isComplete() {
		return estimationRequestPayload != null && customerValidatedPayload != null && vehicleValidatedPayload != null;
	}

}
