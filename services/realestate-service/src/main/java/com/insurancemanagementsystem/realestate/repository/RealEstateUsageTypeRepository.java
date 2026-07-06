package com.insurancemanagementsystem.realestate.repository;

import com.insurancemanagementsystem.realestate.entity.RealEstateUsageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RealEstateUsageTypeRepository extends JpaRepository<RealEstateUsageType, Integer> {
}
