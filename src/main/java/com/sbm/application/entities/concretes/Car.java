package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("Cars")
public class Car extends Entity {

	@Column("carEngineId")
	private int carEngineId;
	@Column("carPackageId")
	private int carPackageId;
	@Column("year")
	private String year;
	@Column("estimatedValue")
	private double estimatedValue;

}
