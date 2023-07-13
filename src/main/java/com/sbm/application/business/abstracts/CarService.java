package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.Car;
import com.sbm.application.entities.dtos.CarDetailDTO;

public interface CarService extends EntityService<Car> {

	DataResult<List<CarDetailDTO>> getCarDetails();
	DataResult<CarDetailDTO> getCarDetailById(int id);
}
