package com.sbm.application.entities.dtos;

import java.time.LocalDate;
import java.time.Period;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
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

	public int getAge() {
		LocalDate date = LocalDate.parse(birthDate);
		return Period.between(date, LocalDate.now()).getYears();
	}
	public int getLicenseAge() {
		LocalDate date = LocalDate.parse(licenseObtainedAt);
		return Period.between(date, LocalDate.now()).getYears();
	}
}
