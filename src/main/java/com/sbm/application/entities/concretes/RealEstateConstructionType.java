package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("RealEstateConstructionTypes")
public class RealEstateConstructionType extends Entity {

	@Column("ConstructionTypeName")
	public String ConstructionTypeName;
}
