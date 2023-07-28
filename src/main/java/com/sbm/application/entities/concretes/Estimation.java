package com.sbm.application.entities.concretes;

import java.time.OffsetDateTime;

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
@Table("Estimations")
public class Estimation extends Entity {

	@Column("InsuranceId")
	private int insuranceId;
	@Column("parameterId")
	private int parameterId;
	@Column("price")
	private double price;
	@Column("estimationDate")
	private OffsetDateTime estimationDate;
	@Column("confirmed")
	private boolean confirmed;

}
