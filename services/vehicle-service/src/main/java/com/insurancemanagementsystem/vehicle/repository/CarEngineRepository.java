package com.insurancemanagementsystem.vehicle.repository;

import com.insurancemanagementsystem.vehicle.entity.CarEngine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarEngineRepository extends JpaRepository<CarEngine, Integer> {
}
