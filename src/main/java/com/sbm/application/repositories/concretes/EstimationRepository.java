package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.Estimation;
import com.sbm.application.entities.dtos.EstimationDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;

@Repository
public interface EstimationRepository extends EntityRepository<Estimation> {

	@Query("""
		SELECT estimation.Id id
				,insurance.Id insurance_id
				,insuranceType.Id insurance_type_id
				,company.Id insurance_company_id
				,insuranceType.Name insurance_type_name
				,company.Name insurance_company_name
				,vehicle.Id parameter_id
				,vehicle.LicensePlate + ' ' + customer.FirstName + ' ' + customer.LastName + ' ' + customer.TC parameter_name
				,estimation.EstimationDate estimation_date
				,estimation.Confirmed confirmed
		  FROM [InsuranceDB].[dbo].[Estimations] estimation
		  JOIN Insurances insurance on insurance.Id = estimation.InsuranceId
		  JOIN InsuranceCompanies company on company.Id = insurance.CompanyId
		  JOIN InsuranceTypes insuranceType on insuranceType.Id = insurance.InsuranceTypeId
		  JOIN Vehicles vehicle on vehicle.Id = estimation.ParameterId AND insuranceType.Name = 'Kasko'
		  JOIN Customers customer on customer.Id = vehicle.CustomerId
			""")
	public Flux<EstimationDetailDTO> findKaskoDetails();
	
	@Query("""
		SELECT estimation.Id id
				,insurance.Id insurance_id
				,insuranceType.Id insurance_type_id
				,company.Id insurance_company_id
				,insuranceType.Name insurance_type_name
				,company.Name insurance_company_name
				,vehicle.Id parameter_id
				,vehicle.LicensePlate + ' ' + customer.FirstName + ' ' + customer.LastName + ' ' + customer.TC parameter_name
				,estimation.EstimationDate estimation_date
				,estimation.Confirmed confirmed
		  FROM [InsuranceDB].[dbo].[Estimations] estimation
		  JOIN Insurances insurance on insurance.Id = estimation.InsuranceId
		  JOIN InsuranceCompanies company on company.Id = insurance.CompanyId
		  JOIN InsuranceTypes insuranceType on insuranceType.Id = insurance.InsuranceTypeId
		  JOIN Vehicles vehicle on vehicle.Id = estimation.ParameterId AND insuranceType.Name = 'Trafik Sigortası'
		  JOIN Customers customer on customer.Id = vehicle.CustomerId
				""")
	public Flux<EstimationDetailDTO> findTrafikSigortaDetails();
}
