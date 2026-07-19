package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsuranceTypeRepository extends JpaRepository<InsuranceType, Integer> {

}
