package com.sbm.application.entities.dtos;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EstimationDetailDTO {
	private int id;
	private int insuranceId;
	private int insuranceTypeId;
	private int insuranceCompanyId;
	private String insuranceTypeName;
	private String insuranceCompanyName;
	private int parameterId;
	private String parameterName;
	private double price;
	private OffsetDateTime estimationDate;
	private boolean confirmed;

}
