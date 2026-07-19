package com.insurancemanagementsystem.vehicle.repository;

import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

	Optional<Vehicle> findByPlate(String plate);

	Page<Vehicle> findByCustomerId(UUID customerId, Pageable pageable);

	@Query("""
			    SELECT DISTINCT v FROM Vehicle v
			    LEFT JOIN CarBrand b ON b.id = v.carBrandId
			    LEFT JOIN CarModel m ON m.id = v.carModelId
			    WHERE LOWER(v.plate) LIKE CONCAT('%', LOWER(:search), '%')
			       OR LOWER(v.chassisNumber) LIKE CONCAT('%', LOWER(:search), '%')
			       OR LOWER(b.name) LIKE CONCAT('%', LOWER(:search), '%')
			       OR LOWER(m.name) LIKE CONCAT('%', LOWER(:search), '%')
			""")
	Page<Vehicle> search(@Param("search") String search, Pageable pageable);

	@Query("""
			    SELECT DISTINCT v FROM Vehicle v
			    LEFT JOIN CarBrand b ON b.id = v.carBrandId
			    LEFT JOIN CarModel m ON m.id = v.carModelId
			    WHERE v.customerId = :customerId
			      AND (LOWER(v.plate) LIKE CONCAT('%', LOWER(:search), '%')
			           OR LOWER(v.chassisNumber) LIKE CONCAT('%', LOWER(:search), '%')
			           OR LOWER(b.name) LIKE CONCAT('%', LOWER(:search), '%')
			           OR LOWER(m.name) LIKE CONCAT('%', LOWER(:search), '%'))
			""")
	Page<Vehicle> searchByCustomerIdAndSearch(@Param("customerId") UUID customerId, @Param("search") String search,
			Pageable pageable);

}
