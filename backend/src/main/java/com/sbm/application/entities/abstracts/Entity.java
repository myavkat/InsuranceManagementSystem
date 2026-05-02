package com.sbm.application.entities.abstracts;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import lombok.Data;

@Data
public abstract class Entity {

	@Id
	@Column("Id")
	private int id;


}
