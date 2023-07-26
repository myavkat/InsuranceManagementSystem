package com.sbm.application.entities.dtos;

import lombok.Data;

@Data
public class RealEstateUnitConstructionCostDetailDTO {

	private int id;
	private int luxuryClassId;
	private int constructionTypeId;
	private int usageTypeId;
	private double unitConstructionCost;
	private String unitConstructionCostTag;
}
