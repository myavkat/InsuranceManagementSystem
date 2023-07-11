package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("InsuranceTypes")
public class InsuranceType extends Entity {

	@Column("name")
	private String name;
	@Column("parameterTable")
	private String parameterTable;


}
