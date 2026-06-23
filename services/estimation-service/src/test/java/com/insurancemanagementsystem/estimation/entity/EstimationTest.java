package com.insurancemanagementsystem.estimation.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EstimationTest {

    @Test
    void onCreate_setsCreatedAtAndUpdatedAt() {
        Estimation estimation = new Estimation();
        estimation.onCreate();

        assertThat(estimation.getCreatedAt()).isNotNull();
        assertThat(estimation.getUpdatedAt()).isEqualTo(estimation.getCreatedAt());
    }

    @Test
    void onUpdate_setsUpdatedAt() {
        Instant initialUpdate = Instant.now().minusSeconds(60);
        Estimation estimation = Estimation.builder()
                .createdAt(Instant.now().minusSeconds(120))
                .updatedAt(initialUpdate)
                .build();

        estimation.onUpdate();

        assertThat(estimation.getUpdatedAt()).isAfter(initialUpdate);
    }

    @Test
    void builder_createsEntityWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Instant now = Instant.now();

        Estimation estimation = Estimation.builder()
                .id(id)
                .sagaId(sagaId)
                .customerId(customerId)
                .vehicleId(vehicleId)
                .insuranceTypeId(1)
                .companyId(UUID.randomUUID())
                .status(Estimation.Status.STARTED)
                .premium(new BigDecimal("1500.00"))
                .details("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(estimation.getId()).isEqualTo(id);
        assertThat(estimation.getSagaId()).isEqualTo(sagaId);
        assertThat(estimation.getCustomerId()).isEqualTo(customerId);
        assertThat(estimation.getVehicleId()).isEqualTo(vehicleId);
        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.STARTED);
        assertThat(estimation.getPremium()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void noArgsConstructor_createsEmptyEntity() {
        Estimation estimation = new Estimation();
        assertThat(estimation.getId()).isNull();
        assertThat(estimation.getStatus()).isNull();
    }

    @Test
    void setters_updateFields() {
        Estimation estimation = new Estimation();
        estimation.setStatus(Estimation.Status.COMPLETED);
        estimation.setPremium(new BigDecimal("2000.00"));
        estimation.setDetails("Completed with premium");

        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
        assertThat(estimation.getPremium()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(estimation.getDetails()).isEqualTo("Completed with premium");
    }
}
