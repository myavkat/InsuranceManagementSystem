package com.insurancemanagementsystem.referencedata.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionEntityTest {

	@Test
	void onCreateShouldSetTimestamps() {
		// Given
		Profession profession = Profession.builder().id(1).name("Doktor").build();

		// Then — timestamps should be null before @PrePersist
		assertThat(profession.getCreatedAt()).isNull();
		assertThat(profession.getUpdatedAt()).isNull();

		// When — simulate @PrePersist
		profession.onCreate();

		// Then — timestamps should be set
		assertThat(profession.getCreatedAt()).isNotNull();
		assertThat(profession.getUpdatedAt()).isNotNull();
	}

	@Test
	void onUpdateShouldUpdateTimestamp() {
		// Given
		Profession profession = Profession.builder().id(1).name("Doktor").build();
		profession.onCreate();
		java.time.Instant originalUpdatedAt = profession.getUpdatedAt();

		// When — simulate @PreUpdate
		profession.onUpdate();

		// Then — updatedAt should be refreshed
		assertThat(profession.getUpdatedAt()).isNotNull();
		assertThat(profession.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
	}

}
