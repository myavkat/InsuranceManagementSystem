package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.Vehicle;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;

@Repository
public interface VehicleRepository extends EntityRepository<Vehicle> {

	@Query("SELECT * FROM Vehicles WHERE Vehicles.customerId = :customerId")
	public Flux<Vehicle> findAllByCustomerId(int customerId);
	
	@Query("SELECT * FROM Vehicles WHERE Vehicles.VIN = :vin")
	public Flux<Vehicle> findAllByVIN(String vin);
	
	@Query("SELECT * FROM Vehicles WHERE Vehicles.LicensePlate = :licensePlate")
	public Flux<Vehicle> findAllByLicensePlate(String licensePlate);
}
