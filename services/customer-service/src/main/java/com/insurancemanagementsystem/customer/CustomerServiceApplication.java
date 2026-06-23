package com.insurancemanagementsystem.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.customer", "com.insurancemanagementsystem.common.web", "com.insurancemanagementsystem.common.messaging"})
public class CustomerServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
