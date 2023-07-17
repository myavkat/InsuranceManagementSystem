package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.City;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Mono;

@Repository
public interface CityRepository extends EntityRepository<City> {

	@Query("""
		SELECT [Id]
		      ,[Name]
		      ,[LicensePlateCode]
		      ,[ValueFactor]
		      ,[ScaleFactor]
		  FROM [InsuranceDB].[dbo].[Cities] city
		  WHERE city.LicensePlateCode = :plateCode
			""")
	public Mono<City> findByPlateCode(String plateCode);
}
