package com.sbm.application.entities.abstracts;

import org.springframework.data.relational.core.mapping.Column;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class Parameter extends Entity {
	
	// Floating-point between 0 and 1: used to scale the basePrice of the insurance
	// Higher means greater the price, eg. 0,57 means increment the price by %57
	@Column("ScaleFactor")
	private double scaleFactor;

	// Price value to increment the insurance price statically and not by ratio
	@Column("ValueFactor")
	private double valueFactor;

}
