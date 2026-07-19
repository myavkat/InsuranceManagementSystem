package com.insurancemanagementsystem.realestate.repository;

import com.insurancemanagementsystem.realestate.entity.RealEstateLuxuryClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RealEstateLuxuryClassRepository extends JpaRepository<RealEstateLuxuryClass, Integer> {

}
