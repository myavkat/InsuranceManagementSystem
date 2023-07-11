package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

@Table("Vehicles")
public class Vehicle extends Parameter {

	@Column("customerId")
	private int customerId;
	@Column("cityId")
	private int cityId;
	@Column("carPackageId")
	private int carPackageId;
	@Column("isDamaged")
	private boolean isDamaged;
	@Column("estimatedValue")
	private double estimatedValue;
	@Column("licensePlate")
	private String licensePlate;

	public Vehicle() {
		super(0, 0, 0);
	}

	public Vehicle(double scaleFactor, double valueFactor, int id, int customerId, int cityId, int carPackageId,
			boolean isDamaged, double estimatedValue, String licensePlate) {
		super(id, scaleFactor, valueFactor);
		this.customerId = customerId;
		this.cityId = cityId;
		this.carPackageId = carPackageId;
		this.isDamaged = isDamaged;
		this.estimatedValue = estimatedValue;
		this.licensePlate = licensePlate;
	}

	public int getCustomerId() {
		return customerId;
	}

	public void setCustomerId(int customerId) {
		this.customerId = customerId;
	}

	public int getCityId() {
		return cityId;
	}

	public void setCityId(int cityId) {
		this.cityId = cityId;
	}

	public int getCarPackageId() {
		return carPackageId;
	}

	public void setCarPackageId(int carPackageId) {
		this.carPackageId = carPackageId;
	}

	public boolean isDamaged() {
		return isDamaged;
	}

	public void setDamaged(boolean isDamaged) {
		this.isDamaged = isDamaged;
	}

	public double getEstimatedValue() {
		return estimatedValue;
	}

	public void setEstimatedValue(double estimatedValue) {
		this.estimatedValue = estimatedValue;
	}

	public String getLicensePlate() {
		return licensePlate;
	}

	public void setLicensePlate(String licensePlate) {
		this.licensePlate = licensePlate;
	}

}
