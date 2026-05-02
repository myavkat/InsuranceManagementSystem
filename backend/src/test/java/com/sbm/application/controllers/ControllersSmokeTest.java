package com.sbm.application.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
public class ControllersSmokeTest {

	@Autowired
	private CustomerController customerController;
	@Autowired
	private EstimationController estimationController;
	@Autowired
	private HomeController homeController;
	@Autowired
	private InsuranceController insuranceController;
	@Autowired
	private InsuranceTypeController insuranceTypeController;
	@Autowired
	private ProfessionController professionController;
	@Autowired
	private RealEstateController realEstateController;
	@Autowired
	private VehicleController vehicleController;
	
	@Test
	public void contextLoads() throws Exception {
		assertThat(customerController).isNotNull();
		assertThat(estimationController).isNotNull();
		assertThat(homeController).isNotNull();
		assertThat(insuranceController).isNotNull();
		assertThat(insuranceTypeController).isNotNull();
		assertThat(professionController).isNotNull();
		assertThat(realEstateController).isNotNull();
		assertThat(vehicleController).isNotNull();
	}
}
