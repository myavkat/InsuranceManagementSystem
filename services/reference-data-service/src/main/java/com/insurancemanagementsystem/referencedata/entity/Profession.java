package com.insurancemanagementsystem.referencedata.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "professions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profession {

	@Id
	@Column(name = "id")
	private Integer id; // INT — NOT generated

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = Instant.now();
		updatedAt = Instant.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}

}
