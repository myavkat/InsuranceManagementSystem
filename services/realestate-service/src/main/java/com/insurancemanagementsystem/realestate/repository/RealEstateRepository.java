package com.insurancemanagementsystem.realestate.repository;

import com.insurancemanagementsystem.realestate.entity.RealEstate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RealEstateRepository extends JpaRepository<RealEstate, UUID> {
    Page<RealEstate> findByCustomerId(UUID customerId, Pageable pageable);
}
