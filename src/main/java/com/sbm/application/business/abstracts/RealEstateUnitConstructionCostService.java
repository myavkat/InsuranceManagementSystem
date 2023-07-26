package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.RealEstateUnitConstructionCost;
import com.sbm.application.entities.dtos.RealEstateUnitConstructionCostDetailDTO;

public interface RealEstateUnitConstructionCostService extends EntityService<RealEstateUnitConstructionCost> {

	public DataResult<List<RealEstateUnitConstructionCostDetailDTO>> getAllDetails();

}
