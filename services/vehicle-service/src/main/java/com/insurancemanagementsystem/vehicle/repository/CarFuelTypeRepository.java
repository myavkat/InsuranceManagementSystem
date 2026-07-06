package com.insurancemanagementsystem.vehicle.repository;

import com.insurancemanagementsystem.vehicle.entity.CarFuelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarFuelTypeRepository extends JpaRepository<CarFuelType, Integer> {
}
