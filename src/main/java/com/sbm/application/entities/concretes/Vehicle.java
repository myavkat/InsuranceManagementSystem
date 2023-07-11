package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

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

	public Vehicle() {
		super(0, 0, 0);
	}

	public Vehicle(double scaleFactor, double valueFactor, int id, int customerId, int carId,
			boolean isDamaged, String licensePlate, String vin) {
		super(id, scaleFactor, valueFactor);
		this.customerId = customerId;
		this.carId = carId;
		this.damaged = isDamaged;
		this.licensePlate = licensePlate;
		this.vin = vin;
	}

	public int getCustomerId() {
		return customerId;
	}

	public void setCustomerId(int customerId) {
		this.customerId = customerId;
	}

	public int getCarId() {
		return carId;
	}

	public void setCarId(int carId) {
		this.carId = carId;
	}

	public boolean isDamaged() {
		return damaged;
	}

	public void setDamaged(boolean isDamaged) {
		this.damaged = isDamaged;
	}

	public String getLicensePlate() {
		return licensePlate;
	}

	public void setLicensePlate(String licensePlate) {
		this.licensePlate = licensePlate;
	}

	public String getVin() {
		return vin;
	}

	public void setVin(String vin) {
		this.vin = vin;
	}

}
