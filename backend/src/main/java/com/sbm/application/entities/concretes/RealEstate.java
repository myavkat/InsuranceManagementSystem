package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("RealEstates")
public class RealEstate extends Entity {

	@NotNull(message = "Boş geçilemez!")
	@Pattern(regexp="^\\d{10}$", message="UAVT kodu 10 haneli sayıdır")
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
	@Pattern(regexp="^(19|20)\\d\\d$", message="Yıl geçersiz")
	@Column("ConstructionYear")
	private String constructionYear;
	@NotNull(message = "Boş geçilemez!")
	@NotEmpty(message= "Lütfen adres giriniz")
	@Column("Address")
	private String address;
	@NotNull(message = "Boş geçilemez!")
	@Column("UrbanLocated")
	private boolean urbanLocated;

}
