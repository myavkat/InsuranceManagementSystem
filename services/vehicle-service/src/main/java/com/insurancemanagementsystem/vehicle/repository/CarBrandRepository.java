package com.insurancemanagementsystem.vehicle.repository;

import com.insurancemanagementsystem.vehicle.entity.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarBrandRepository extends JpaRepository<CarBrand, Integer> {

}
