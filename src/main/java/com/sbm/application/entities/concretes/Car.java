package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

@Table("Cars")
public class Car extends Parameter {

	@Column("carEngineId")
	private int carEngineId;
	@Column("carPackageId")
	private int carPackageId;
	@Column("year")
	private String year;
	@Column("estimatedValue")
	private double estimatedValue;

	public Car() {
		super(0, 0, 0);
	}

	public Car(double scaleFactor, double valueFactor, int id, int carEngineId, int carPackageId,
			double estimatedValue, String year) {
		super(id, scaleFactor, valueFactor);
		this.carEngineId = carEngineId;
		this.carPackageId = carPackageId;
		this.estimatedValue = estimatedValue;
		this.year = year;
	}

	public int getCarEngineId() {
		return carEngineId;
	}

	public void setCarEngineId(int carEngineId) {
		this.carEngineId = carEngineId;
	}

	public int getCarPackageId() {
		return carPackageId;
	}

	public void setCarPackageId(int carPackageId) {
		this.carPackageId = carPackageId;
	}

	public double getEstimatedValue() {
		return estimatedValue;
	}

	public void setEstimatedValue(double estimatedValue) {
		this.estimatedValue = estimatedValue;
	}

	public String getYear() {
		return year;
	}

	public void setYear(String year) {
		this.year = year;
	}

}
