package com.insurancemanagementsystem.vehicle.repository;

import com.insurancemanagementsystem.vehicle.entity.CarPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarPackageRepository extends JpaRepository<CarPackage, Integer> {
}
