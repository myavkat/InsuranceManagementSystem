package com.sbm.application.entities.dtos;

import lombok.Data;

@Data
public class RealEstateDetailDTO {
	public int id;
	public String uavt;
	public int unitConstructionCostId;
	public double unitConstructionCost;
	public String unitConstructionCostTag;
	public int cityId;
	public String cityName;
	public double cityScaleFactor;
	public double cityValueFactor;
	public int customerId;
	public String customerTag;
	public double value;
	public int floorArea;
	public String constructionYear;
	public String address;
	public boolean urbanLocated;
}
