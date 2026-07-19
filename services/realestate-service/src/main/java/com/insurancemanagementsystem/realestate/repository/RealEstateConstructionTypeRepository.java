package com.insurancemanagementsystem.realestate.repository;

import com.insurancemanagementsystem.realestate.entity.RealEstateConstructionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RealEstateConstructionTypeRepository extends JpaRepository<RealEstateConstructionType, Integer> {

}
