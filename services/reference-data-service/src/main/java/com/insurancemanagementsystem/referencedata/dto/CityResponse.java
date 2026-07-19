package com.insurancemanagementsystem.referencedata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityResponse {

	private Integer id;

	private String name;

	private String plateCode;

}
