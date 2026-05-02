package com.sbm.application.business.abstracts;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.City;

public interface CityService extends EntityService<City> {

	public DataResult<City> getByPlateCode(String plateCode);
}
