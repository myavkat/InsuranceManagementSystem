package com.sbm.application.entities.concretes;

import java.sql.Timestamp;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("Estimations")
public class Estimation extends Entity {

	@Column("insuranceId")
	private int insuranceId;
	@Column("parameterId")
	private int parameterId;
	@Column("price")
	private double price;
	@Column("estimationDate")
	private Timestamp estimationDate;
	@Column("confirmed")
	private boolean confirmed;

}
