package com.insurancemanagementsystem.realestate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.realestate",
    "com.insurancemanagementsystem.common.web",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.realestate.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class RealEstateServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(RealEstateServiceApplication.class, args);
    }
}
