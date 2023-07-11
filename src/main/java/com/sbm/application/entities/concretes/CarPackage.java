package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

@Table("CarPackages")
public class CarPackage extends Parameter {

	@Column("name")
	private String name;
	@Column("carModelId")
	private int carModelId;

	public CarPackage() {
		super(0, 0, 0);
	}

	public CarPackage(double scaleFactor, double valueFactor, int id, int carModelId, String name) {
		super(id, scaleFactor, valueFactor);
		this.name = name;
		this.carModelId = carModelId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getCarModelId() {
		return carModelId;
	}

	public void setCarModelId(int carModelId) {
		this.carModelId = carModelId;
	}

}
