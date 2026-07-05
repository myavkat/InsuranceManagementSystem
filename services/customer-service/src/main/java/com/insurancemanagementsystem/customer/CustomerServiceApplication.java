package com.insurancemanagementsystem.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.customer", "com.insurancemanagementsystem.common.web", "com.insurancemanagementsystem.common.messaging", "com.insurancemanagementsystem.common.config"})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.customer.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class CustomerServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
