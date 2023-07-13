package com.sbm.application.business.abstracts;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.Estimation;

public interface EstimationService extends EntityService<Estimation> {

	public DataResult<Estimation> estimateKasko(int insuranceId, int vehicleId); 
}
