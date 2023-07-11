package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("CarEngines")
public class CarEngine extends Parameter {
	@Column("carFuelTypeId")
	private int carFuelTypeId;
	@Column("carEngineSizeId")
	private int carEngineSizeId;

}
