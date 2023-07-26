package com.sbm.application.entities.dtos;

import lombok.Data;

@Data
public class RealEstateDetailDTO {
	private int id;
	private String uavt;
	private int unitConstructionCostId;
	private double unitConstructionCost;
	private String unitConstructionCostTag;
	private int cityId;
	private String cityName;
	private double cityScaleFactor;
	private double cityValueFactor;
	private int customerId;
	private String customerTag;
	private double value;
	private int floorArea;
	private String constructionYear;
	private String address;
	private boolean urbanLocated;
}
