package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "insurance_types")
public class InsuranceType {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
