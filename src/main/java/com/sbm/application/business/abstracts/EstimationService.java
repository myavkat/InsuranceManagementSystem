package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.entities.concretes.Estimation;
import com.sbm.application.entities.dtos.EstimationDetailDTO;

public interface EstimationService extends EntityService<Estimation> {

	public DataResult<Estimation> estimateKasko(int insuranceId, int vehicleId); 
	public DataResult<List<EstimationDetailDTO>> estimateKaskoAllCompanies(int vehicleId);
	public DataResult<List<EstimationDetailDTO>> getDetails();
	public DataResult<List<EstimationDetailDTO>> getKaskoDetailsByVehicleId(int vehicleId);
	public DataResult<List<EstimationDetailDTO>> getDetailsByCustomerId(int customerId);
	public Result confirmById(int id);
	public Result revokeConfirmationById(int id);
	public DataResult<Estimation> estimateKonut(int insuranceId, int realEstateId); 
	public DataResult<List<EstimationDetailDTO>> estimateKonutAllCompanies(int realEstateId);
	public DataResult<List<EstimationDetailDTO>> getKonutDetailsByRealEstateId(int realEstateId);
}
