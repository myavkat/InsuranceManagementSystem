package com.insurancemanagementsystem.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.insurance", "com.insurancemanagementsystem.common.web", "com.insurancemanagementsystem.common.messaging"})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.insurance.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class InsuranceServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(InsuranceServiceApplication.class, args);
    }
}
