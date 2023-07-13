package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.Insurance;
import com.sbm.application.entities.dtos.InsuranceDetailDTO;

public interface InsuranceService extends EntityService<Insurance> {

	public DataResult<InsuranceDetailDTO> getInsuranceDetailById(int id);
	public DataResult<List<InsuranceDetailDTO>> getInsuranceDetails();
	
}
