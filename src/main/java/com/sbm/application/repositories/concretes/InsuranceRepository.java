package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.Insurance;
import com.sbm.application.entities.dtos.InsuranceDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface InsuranceRepository extends EntityRepository<Insurance> {

	@Query("""
		SELECT insurance.[Id]
		      ,insurance.[CompanyId] insurance_company_id
		      ,insurance.[InsuranceTypeId] insurance_type_id
		      ,insurance.[UnitPrice] unit_price
			  ,insuranceType.Name insurance_type_name
			  ,insuranceCompany.Name insurance_company_name
		  FROM [InsuranceDB].[dbo].[Insurances] insurance
		  JOIN InsuranceTypes insuranceType on insurance.InsuranceTypeId = insuranceType.Id
		  JOIN InsuranceCompanies insuranceCompany on insurance.CompanyId = insuranceCompany.Id
		  WHERE insurance.Id = :id
			""")
	public Mono<InsuranceDetailDTO> getInsuranceDetailById(int id);
	
	@Query("""
			SELECT insurance.[Id]
			      ,insurance.[CompanyId] insurance_company_id
			      ,insurance.[InsuranceTypeId] insurance_type_id
			      ,insurance.[UnitPrice] unit_price
				  ,insuranceType.Name insurance_type_name
				  ,insuranceCompany.Name insurance_company_name
			  FROM [InsuranceDB].[dbo].[Insurances] insurance
			  JOIN InsuranceTypes insuranceType on insurance.InsuranceTypeId = insuranceType.Id
			  JOIN InsuranceCompanies insuranceCompany on insurance.CompanyId = insuranceCompany.Id
				""")
		public Flux<InsuranceDetailDTO> getInsuranceDetails();
}
