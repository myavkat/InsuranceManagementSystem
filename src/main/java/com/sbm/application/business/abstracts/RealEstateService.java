package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.RealEstate;
import com.sbm.application.entities.dtos.RealEstateDetailDTO;

public interface RealEstateService extends EntityService<RealEstate> {

	public DataResult<List<RealEstateDetailDTO>> getAllDetails();
	public DataResult<RealEstateDetailDTO> getDetailById(int id);
}
