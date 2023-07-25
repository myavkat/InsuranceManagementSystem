package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("RealEstateUnitConstructionCosts")
public class RealEstateUnitConstructionCost extends Entity {

	@Column("LuxuryClassId")
	public int luxuryClassId;
	@Column("ConstructionTypeId")
	public int constructionTypeId;
	@Column("UsageTypeId")
	public int usageTypeId;
	@Column("UnitConstructionCost")
	public double unitConstructionCost;
}
