package com.sbm.application.entities.dtos;

import lombok.Data;

@Data
public class InsuranceDetailDTO {

	private int id;
	private int insuranceTypeId;
	private int insuranceCompanyId;
	private String insuranceTypeName;
	private String insuranceCompanyName;
	private double unitPrice;
}
