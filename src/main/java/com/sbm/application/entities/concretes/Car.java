package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

@Table("Cars")
public class Car extends Parameter {

	@Column("carModelId")
	private int carModelId;
	@Column("carEngineId")
	private int carEngineId;
	@Column("carTypeId")
	private int carTypeId;
	@Column("year")
	private String year;

	public Car() {
		super(0, 0, 0);
	}

	public Car(double scaleFactor, double valueFactor, int id, int carModelId, int carEngineId, int carTypeId,
			String year) {
		super(id, scaleFactor, valueFactor);
		this.carModelId = carModelId;
		this.carEngineId = carEngineId;
		this.carTypeId = carTypeId;
		this.year = year;
	}

	public int getCarModelId() {
		return carModelId;
	}

	public void setCarModelId(int carModelId) {
		this.carModelId = carModelId;
	}

	public int getCarEngineId() {
		return carEngineId;
	}

	public void setCarEngineId(int carEngineId) {
		this.carEngineId = carEngineId;
	}

	public int getCarTypeId() {
		return carTypeId;
	}

	public void setCarTypeId(int carTypeId) {
		this.carTypeId = carTypeId;
	}

	public String getYear() {
		return year;
	}

	public void setYear(String year) {
		this.year = year;
	}

}
