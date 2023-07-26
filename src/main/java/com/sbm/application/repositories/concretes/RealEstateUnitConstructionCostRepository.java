package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.RealEstateUnitConstructionCost;
import com.sbm.application.entities.dtos.RealEstateUnitConstructionCostDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;

@Repository
public interface RealEstateUnitConstructionCostRepository extends EntityRepository<RealEstateUnitConstructionCost> {

	@Query("""
		SELECT ucc.[Id] as id
		      ,[LuxuryClassId] as luxury_class_id
		      ,[ConstructionTypeId] as construction_type_id
		      ,[UsageTypeId] as usage_type_id
		      ,[UnitConstructionCost] as unit_construction_cost
			  ,LuxuryClassName + ' ' + ConstructionTypeName + ' ' + UsageTypeName as unit_construction_cost_tag
		  FROM [InsuranceDB].[dbo].[RealEstateUnitConstructionCosts] ucc
		  JOIN [InsuranceDB].[dbo].[RealEstateLuxuryClasses] luxuryClass on luxuryClass.Id = ucc.LuxuryClassId
		  JOIN [InsuranceDB].[dbo].[RealEstateConstructionTypes] constructionType on constructionType.Id = ucc.ConstructionTypeId
		  JOIN [InsuranceDB].[dbo].[RealEstateUsageTypes] usageType on usageType.Id = ucc.UsageTypeId
			""")
	public Flux<RealEstateUnitConstructionCostDetailDTO> findAllDetails();
}
