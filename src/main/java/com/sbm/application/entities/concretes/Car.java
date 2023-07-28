package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
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
