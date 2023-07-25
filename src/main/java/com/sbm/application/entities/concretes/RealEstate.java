package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("RealEstates")
public class RealEstate extends Entity {

	@Column("UAVT")
	public String uavt;
	@Column("UnitConstructionCostId")
	public int unitConstructionCostId;
	@Column("CityId")
	public int cityId;
	@Column("CustomerId")
	public int customerId;
	@Column("Value")
	public double value;
	@Column("FloorArea")
	public int floorArea;
	@Column("ConstructionYear")
	public String constructionYear;
	@Column("Address")
	public String address;
	@Column("UrbanLocation")
	public boolean urbanLocated;
	
}
