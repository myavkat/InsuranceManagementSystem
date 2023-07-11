package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("Vehicles")
public class Vehicle extends Parameter {

	@Column("customerId")
	private int customerId;
	@Column("carId")
	private int carId;
	@Column("isDamaged")
	private boolean damaged;
	@Column("licensePlate")
	private String licensePlate;
	@Column("VIN")
	private String vin;

}
