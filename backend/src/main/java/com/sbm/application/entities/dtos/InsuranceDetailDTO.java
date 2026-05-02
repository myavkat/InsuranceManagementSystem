package com.sbm.application.entities.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsuranceDetailDTO {

	private int id;
	private int insuranceTypeId;
	private int insuranceCompanyId;
	private String insuranceTypeName;
	private String insuranceCompanyName;
	private double unitPrice;
}
