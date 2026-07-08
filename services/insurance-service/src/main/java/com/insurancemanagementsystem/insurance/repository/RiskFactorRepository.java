package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.RiskFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskFactorRepository extends JpaRepository<RiskFactor, UUID> {
    List<RiskFactor> findByInsuranceId(UUID insuranceId);
    Optional<RiskFactor> findByInsuranceIdAndFactorName(UUID insuranceId, String factorName);
}
