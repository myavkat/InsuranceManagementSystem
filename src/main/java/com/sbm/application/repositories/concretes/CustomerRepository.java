package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.Customer;
import com.sbm.application.entities.dtos.CustomerDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CustomerRepository extends EntityRepository<Customer>{
	
	@Query("""
		SELECT customer.[Id] 
		      ,[ProfessionId] profession_id
		      ,[CityOfResidenceId] city_of_residence_id
		      ,[FirstName] first_name
		      ,[LastName] last_name
		      ,[TC] tc
		      ,[PhoneNumber] phone_number
		      ,[BirthDate] birth_date
		      ,[LicenseObtainedAt] license_obtained_at
			  ,city.Name city_of_residence_name
			  ,city.ScaleFactor city_scale_factor
			  ,city.ValueFactor city_value_factor
			  ,profession.Name profession_name
			  ,profession.ScaleFactor profession_scale_factor
			  ,profession.ValueFactor profession_value_factor
		  FROM [InsuranceDB].[dbo].[Customers] customer
		  JOIN Cities city on customer.CityOfResidenceId = city.Id
		  JOIN Professions profession on customer.ProfessionId = profession.Id
		  WHERE customer.Id = :id
			""")
	public Mono<CustomerDetailDTO> getCustomerDetailById(int id);
	
	@Query("""
		SELECT customer.[Id] 
		      ,[ProfessionId] profession_id
		      ,[CityOfResidenceId] city_of_residence_id
		      ,[FirstName] first_name
		      ,[LastName] last_name
		      ,[TC] tc
		      ,[PhoneNumber] phone_number
		      ,[BirthDate] birth_date
		      ,[LicenseObtainedAt] license_obtained_at
			  ,city.Name city_of_residence_name
			  ,city.ScaleFactor city_scale_factor
			  ,city.ValueFactor city_value_factor
			  ,profession.Name profession_name
			  ,profession.ScaleFactor profession_scale_factor
			  ,profession.ValueFactor profession_value_factor
		  FROM [InsuranceDB].[dbo].[Customers] customer
		  JOIN Cities city on customer.CityOfResidenceId = city.Id
		  JOIN Professions profession on customer.ProfessionId = profession.Id
			""")
	public Flux<CustomerDetailDTO> getCustomerDetails();
}
