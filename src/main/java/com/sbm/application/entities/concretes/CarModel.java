package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("CarModels")
public class CarModel extends Entity {

	@Column("brandId")
	private int brandId;
	@Column("carTypeId")
	private int carTypeId;
	@Column("name")
	private String name;

}
