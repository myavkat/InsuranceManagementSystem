package com.insurancemanagementsystem.estimation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.estimation", "com.insurancemanagementsystem.common.web", "com.insurancemanagementsystem.common.messaging"})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.estimation.entity",
    "com.insurancemanagementsystem.common.entity"
})
@EnableScheduling
public class EstimationServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(EstimationServiceApplication.class, args);
    }
}
