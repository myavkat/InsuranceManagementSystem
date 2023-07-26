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
	private String uavt;
	@Column("UnitConstructionCostId")
	private int unitConstructionCostId;
	@Column("CityId")
	private int cityId;
	@Column("CustomerId")
	private int customerId;
	@Column("Value")
	private double value;
	@Column("FloorArea")
	private int floorArea;
	@Column("ConstructionYear")
	private String constructionYear;
	@Column("Address")
	private String address;
	@Column("UrbanLocation")
	private boolean urbanLocated;

}
