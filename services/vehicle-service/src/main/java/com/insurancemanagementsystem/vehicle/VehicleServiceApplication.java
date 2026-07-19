package com.insurancemanagementsystem.vehicle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(
		scanBasePackages = { "com.insurancemanagementsystem.vehicle", "com.insurancemanagementsystem.common.web",
				"com.insurancemanagementsystem.common.messaging", "com.insurancemanagementsystem.common.config" })
@EntityScan(basePackages = { "com.insurancemanagementsystem.vehicle.entity",
		"com.insurancemanagementsystem.common.entity" })
public class VehicleServiceApplication {

	static void main(String[] args) {
		SpringApplication.run(VehicleServiceApplication.class, args);
	}

}
