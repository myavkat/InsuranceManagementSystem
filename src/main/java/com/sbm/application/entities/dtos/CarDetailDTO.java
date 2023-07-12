package com.sbm.application.entities.dtos;

import lombok.Data;

@Data
public class CarDetailDTO {

	private int id;
	private double estimatedValue;
	private String year;
	private String fuelTypeName;
	private String engineSize;
	private String carTypeName;
	private String carName;
	private double valueFactor;
	private double scaleFactor;
	private int carEngineId;
	private int carPackageId;
	private int carEngineSizeId;
	private int carFuelTypeId;
	private int carModelId;
	private int carBrandId;
	private int carTypeId;
	
	
}
