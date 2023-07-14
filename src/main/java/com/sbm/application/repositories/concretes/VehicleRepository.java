package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;

import com.sbm.application.entities.concretes.Vehicle;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;

public interface VehicleRepository extends EntityRepository<Vehicle> {

	@Query("""
			SELECT * FROM Vehicles WHERE Vehicles.customerId = :customerId
			""")
	public Flux<Vehicle> findByCustomerId(int customerId);
}
