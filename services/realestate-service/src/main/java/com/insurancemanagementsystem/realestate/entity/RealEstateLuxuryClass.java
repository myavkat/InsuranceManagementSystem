package com.insurancemanagementsystem.realestate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "real_estate_luxury_classes")
public class RealEstateLuxuryClass {

    @Id
    private Integer id;

    @Column(name = "name", length = 100, unique = true, nullable = false)
    private String name;
}
