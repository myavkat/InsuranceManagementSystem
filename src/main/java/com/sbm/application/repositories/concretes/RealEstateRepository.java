package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.RealEstate;
import com.sbm.application.entities.dtos.RealEstateDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RealEstateRepository extends EntityRepository<RealEstate> {

	@Query("SELECT * FROM [InsuranceDB].[dbo].[RealEstates] WHERE UAVT = :uavt ")
	public Flux<RealEstate> findAllByUAVT(String uavt);
	@Query("""
		SELECT realEstate.[Id] as id
		      ,realEstate.[UAVT] as uavt
		      ,realEstate.[UnitConstructionCostId] as unit_construction_cost_id
			  ,unitConstructionCost.UnitConstructionCost as unit_construction_cost
			  ,LuxuryClassName + ' ' + ConstructionTypeName + ' ' + UsageTypeName as unit_construction_cost_tag
		      ,realEstate.[CityId] as city_id
			  ,city."Name" as city_name
			  ,city.ScaleFactor as city_scale_factor
			  ,city.ValueFactor as city_value_factor
		      ,realEstate.[CustomerId] as customer_id
			  ,customer.TC + ' '  + customer.FirstName + ' '  + customer.LastName as customer_tag
		      ,realEstate.[Value] as "value"
		      ,realEstate.[FloorArea] as floor_area
		      ,realEstate.[ConstructionYear] as construction_year
		      ,realEstate.[Address] as "address"
		      ,realEstate.[UrbanLocated] as urban_located
		  FROM [InsuranceDB].[dbo].[RealEstates] realEstate
		  JOIN [InsuranceDB].[dbo].[RealEstateUnitConstructionCosts] unitConstructionCost ON unitConstructionCost.Id = realEstate.UnitConstructionCostId
		  JOIN [InsuranceDB].[dbo].[RealEstateLuxuryClasses] luxuryClass ON luxuryClass.Id = unitConstructionCost.LuxuryClassId
		  JOIN [InsuranceDB].[dbo].[RealEstateConstructionTypes] constructionType ON constructionType.Id = unitConstructionCost.ConstructionTypeId
		  JOIN [InsuranceDB].[dbo].[RealEstateUsageTypes] usageType ON usageType.Id = unitConstructionCost.UsageTypeId
		  JOIN [InsuranceDB].[dbo].[Customers] customer on customer.Id = realEstate.CustomerId
		  JOIN [InsuranceDB].[dbo].[Cities] city on city.Id = realEstate.CityId
			""")
	public Flux<RealEstateDetailDTO> findAllDetails();
	
	@Query("""
			SELECT realEstate.[Id] as id
			      ,realEstate.[UAVT] as uavt
			      ,realEstate.[UnitConstructionCostId] as unit_construction_cost_id
				  ,unitConstructionCost.UnitConstructionCost as unit_construction_cost
				  ,LuxuryClassName + ' ' + ConstructionTypeName + ' ' + UsageTypeName as unit_construction_cost_tag
			      ,realEstate.[CityId] as city_id
				  ,city."Name" as city_name
				  ,city.ScaleFactor as city_scale_factor
				  ,city.ValueFactor as city_value_factor
			      ,realEstate.[CustomerId] as customer_id
				  ,customer.TC + ' '  + customer.FirstName + ' '  + customer.LastName as customer_tag
			      ,realEstate.[Value] as "value"
			      ,realEstate.[FloorArea] as floor_area
			      ,realEstate.[ConstructionYear] as construction_year
			      ,realEstate.[Address] as "address"
			      ,realEstate.[UrbanLocated] as urban_located
			  FROM [InsuranceDB].[dbo].[RealEstates] realEstate
			  JOIN [InsuranceDB].[dbo].[RealEstateUnitConstructionCosts] unitConstructionCost ON unitConstructionCost.Id = realEstate.UnitConstructionCostId
			  JOIN [InsuranceDB].[dbo].[RealEstateLuxuryClasses] luxuryClass ON luxuryClass.Id = unitConstructionCost.LuxuryClassId
			  JOIN [InsuranceDB].[dbo].[RealEstateConstructionTypes] constructionType ON constructionType.Id = unitConstructionCost.ConstructionTypeId
			  JOIN [InsuranceDB].[dbo].[RealEstateUsageTypes] usageType ON usageType.Id = unitConstructionCost.UsageTypeId
			  JOIN [InsuranceDB].[dbo].[Customers] customer on customer.Id = realEstate.CustomerId
			  JOIN [InsuranceDB].[dbo].[Cities] city on city.Id = realEstate.CityId
			  WHERE realEstate.[Id] = :id
				""")
		public Mono<RealEstateDetailDTO> findDetailById(int id);
}
