package com.insurancemanagementsystem.referencedata.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CityEntityTest {

    @Test
    void onCreateShouldSetTimestamps() {
        // Given
        City city = City.builder()
                .id(6)
                .name("Ankara")
                .plateCode("06")
                .build();

        // Then — timestamps should be null before @PrePersist
        assertThat(city.getCreatedAt()).isNull();
        assertThat(city.getUpdatedAt()).isNull();

        // When — simulate @PrePersist
        city.onCreate();

        // Then — timestamps should be set
        assertThat(city.getCreatedAt()).isNotNull();
        assertThat(city.getUpdatedAt()).isNotNull();
    }

    @Test
    void onUpdateShouldUpdateTimestamp() {
        // Given
        City city = City.builder()
                .id(6)
                .name("Ankara")
                .plateCode("06")
                .build();
        city.onCreate();
        java.time.Instant originalUpdatedAt = city.getUpdatedAt();

        // When — simulate @PreUpdate
        city.onUpdate();

        // Then — updatedAt should be refreshed
        assertThat(city.getUpdatedAt()).isNotNull();
        assertThat(city.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
}
