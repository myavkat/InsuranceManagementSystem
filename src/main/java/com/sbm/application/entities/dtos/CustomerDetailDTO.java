package com.sbm.application.entities.dtos;

import lombok.Data;

@Data
public class CustomerDetailDTO {
	private int id;
	private int professionId;
	private String professionName;
	private double professionScaleFactor;
	private double professionValueFactor;
	private int cityOfResidenceId;
	private double cityScaleFactor;
	private double cityValueFactor;
	private String cityOfResidenceName;
	private String firstName;
	private String lastName;
	private String tc;
	private String phoneNumber;
	private String birthDate;
	private String licenseObtainedAt;
}
