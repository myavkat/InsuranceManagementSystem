package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InsuranceCompanyRepository extends JpaRepository<InsuranceCompany, UUID> {
    Page<InsuranceCompany> findByIsActiveTrue(Pageable pageable);
    Page<InsuranceCompany> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
