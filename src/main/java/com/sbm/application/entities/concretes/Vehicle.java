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

	@Column("CustomerId")
	private int customerId;
	@Column("CarId")
	private int carId;
	@Column("Damaged")
	private boolean damaged;
	@Column("LicensePlate")
	private String licensePlate;
	@Column("VIN")
	private String vin;

}
