package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.Estimation;
import com.sbm.application.entities.dtos.EstimationDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
				,estimation.Price price
				,vehicle.LicensePlate + ' ' + customer.FirstName + ' ' + customer.LastName + ' ' + customer.TC parameter_name
				,CONVERT(NVARCHAR(255), estimation.EstimationDate, 121) estimation_date
				,estimation.Confirmed confirmed
		  FROM [InsuranceDB].[dbo].[Estimations] estimation
		  JOIN Insurances insurance on insurance.Id = estimation.InsuranceId
		  JOIN InsuranceCompanies company on company.Id = insurance.CompanyId
		  JOIN InsuranceTypes insuranceType on insuranceType.Id = insurance.InsuranceTypeId
		  JOIN Vehicles vehicle on vehicle.Id = estimation.ParameterId AND insuranceType.Name = 'Kasko'
		  JOIN Customers customer on customer.Id = vehicle.CustomerId
		  ORDER BY estimationDate DESC
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
					,estimation.Price price
					,vehicle.LicensePlate + ' ' + customer.FirstName + ' ' + customer.LastName + ' ' + customer.TC parameter_name
					,CONVERT(NVARCHAR(255), estimation.EstimationDate, 121) estimation_date
					,estimation.Confirmed confirmed
			  FROM [InsuranceDB].[dbo].[Estimations] estimation
			  JOIN Insurances insurance on insurance.Id = estimation.InsuranceId
			  JOIN InsuranceCompanies company on company.Id = insurance.CompanyId
			  JOIN InsuranceTypes insuranceType on insuranceType.Id = insurance.InsuranceTypeId
			  JOIN Vehicles vehicle on vehicle.Id = estimation.ParameterId AND insuranceType.Name = 'Kasko'
			  JOIN Customers customer on customer.Id = vehicle.CustomerId
			  WHERE customer.Id = :customerId
			  ORDER BY estimationDate DESC
				""")
	public Flux<EstimationDetailDTO> findKaskoDetailsByCustomerId(int customerId);
	@Query("""
			SELECT estimation.Id id
					,insurance.Id insurance_id
					,insuranceType.Id insurance_type_id
					,company.Id insurance_company_id
					,insuranceType.Name insurance_type_name
					,company.Name insurance_company_name
					,vehicle.Id parameter_id
					,estimation.Price price
					,vehicle.LicensePlate + ' ' + customer.FirstName + ' ' + customer.LastName + ' ' + customer.TC parameter_name
					,CONVERT(NVARCHAR(255), estimation.EstimationDate, 121) estimation_date
					,estimation.Confirmed confirmed
			  FROM [InsuranceDB].[dbo].[Estimations] estimation
			  JOIN Insurances insurance on insurance.Id = estimation.InsuranceId
			  JOIN InsuranceCompanies company on company.Id = insurance.CompanyId
			  JOIN InsuranceTypes insuranceType on insuranceType.Id = insurance.InsuranceTypeId
			  JOIN Vehicles vehicle on vehicle.Id = estimation.ParameterId AND insuranceType.Name = 'Kasko'
			  JOIN Customers customer on customer.Id = vehicle.CustomerId
			  WHERE vehicle.Id = :vehicleId
			  ORDER BY estimationDate DESC
				""")
	public Flux<EstimationDetailDTO> findKaskoDetailsByVehicleId(int vehicleId);
	@Query("""
		SELECT estimation.Id id
				,insurance.Id insurance_id
				,insuranceType.Id insurance_type_id
				,company.Id insurance_company_id
				,insuranceType.Name insurance_type_name
				,company.Name insurance_company_name
				,vehicle.Id parameter_id
				,estimation.Price price
				,vehicle.LicensePlate + ' ' + customer.FirstName + ' ' + customer.LastName + ' ' + customer.TC parameter_name
				,CONVERT(NVARCHAR(255), estimation.EstimationDate, 121) estimation_date
				,estimation.Confirmed confirmed
		  FROM [InsuranceDB].[dbo].[Estimations] estimation
		  JOIN Insurances insurance on insurance.Id = estimation.InsuranceId
		  JOIN InsuranceCompanies company on company.Id = insurance.CompanyId
		  JOIN InsuranceTypes insuranceType on insuranceType.Id = insurance.InsuranceTypeId
		  JOIN Vehicles vehicle on vehicle.Id = estimation.ParameterId AND insuranceType.Name = 'Trafik Sigortası'
		  JOIN Customers customer on customer.Id = vehicle.CustomerId
		  ORDER BY estimationDate DESC
				""")
	public Flux<EstimationDetailDTO> findTrafikSigortaDetails();
	@Override
	@Query("""
		SELECT estimation.Id id
				,estimation.InsuranceId
				,estimation.ParameterId
				,estimation.Price
				,CONVERT(NVARCHAR(255), estimation.EstimationDate, 121) estimationDate
				,estimation.Confirmed confirmed
		  FROM [InsuranceDB].[dbo].[Estimations] estimation
		  ORDER BY estimationDate DESC
			""")
	public Flux<Estimation> findAll();
	@Override
	@Query("""
		SELECT estimation.Id id
				,estimation.InsuranceId
				,estimation.ParameterId
				,estimation.Price
				,CONVERT(NVARCHAR(255), estimation.EstimationDate, 121) estimationDate
				,estimation.Confirmed confirmed
		  FROM [InsuranceDB].[dbo].[Estimations] estimation
		  WHERE estimation.Id = :id
			""")
	public Mono<Estimation> findById(Integer id);
}
