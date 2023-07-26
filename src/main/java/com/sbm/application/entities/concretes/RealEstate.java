package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("RealEstates")
public class RealEstate extends Entity {

	@NotNull(message = "Boş geçilemez!")
	@Column("UAVT")
	private String uavt;
	@NotNull(message = "Boş geçilemez!")
	@Column("UnitConstructionCostId")
	private int unitConstructionCostId;
	@NotNull(message = "Boş geçilemez!")
	@Column("CityId")
	private int cityId;
	@NotNull(message = "Boş geçilemez!")
	@Column("CustomerId")
	private int customerId;
	@NotNull(message = "Boş geçilemez!")
	@Column("Value")
	private double value;
	@NotNull(message = "Boş geçilemez!")
	@Column("FloorArea")
	private int floorArea;
	@NotNull(message = "Boş geçilemez!")
	@Column("ConstructionYear")
	private String constructionYear;
	@NotNull(message = "Boş geçilemez!")
	@Column("Address")
	private String address;
	@NotNull(message = "Boş geçilemez!")
	@Column("UrbanLocation")
	private boolean urbanLocated;

}
