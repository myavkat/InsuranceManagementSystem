package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.RiskFactorHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RiskFactorHistoryRepository extends JpaRepository<RiskFactorHistory, UUID> {
    Page<RiskFactorHistory> findByInsuranceIdOrderByChangedAtDesc(UUID insuranceId, Pageable pageable);
    Page<RiskFactorHistory> findByRiskFactorIdOrderByChangedAtDesc(UUID riskFactorId, Pageable pageable);
}
