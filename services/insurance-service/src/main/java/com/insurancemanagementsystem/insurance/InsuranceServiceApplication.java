package com.insurancemanagementsystem.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.insurance", "com.insurancemanagementsystem.common.web", "com.insurancemanagementsystem.common.messaging"})
public class InsuranceServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(InsuranceServiceApplication.class, args);
    }
}
