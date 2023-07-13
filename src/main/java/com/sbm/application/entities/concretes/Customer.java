package com.sbm.application.entities.concretes;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Parameter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("Customers")
public class Customer extends Parameter {

	@Column("professionId")
	private int professionId;
	@Column("cityOfResidenceId")
	private int cityOfResidenceId;
	@Column("firstName")
	private String firstName;
	@Column("lastName")
	private String lastName;
	@Column("tc")
	private String tc;
	@Column("phoneNumber")
	private String phoneNumber;
	@Column("birthDate")
	private String birthDate;
	@Column("licenseObtainedAt")
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
