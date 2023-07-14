package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.Vehicle;

public interface VehicleService extends EntityService<Vehicle> {

	public DataResult<List<Vehicle>> getVehiclesByCustomerId(int customerId);
}
