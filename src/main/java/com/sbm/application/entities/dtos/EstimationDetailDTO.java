package com.sbm.application.entities.dtos;

import java.sql.Timestamp;

import lombok.Data;

@Data
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
	private Timestamp estimationDate;

}
