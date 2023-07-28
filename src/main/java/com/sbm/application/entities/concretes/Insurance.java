package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("Insurances")
public class Insurance extends Entity {

	@Column("companyId")
	private int companyId;
	@Column("insuranceTypeId")
	private int insuranceTypeId;
	@Column("unitPrice")
	private double unitPrice;


}
