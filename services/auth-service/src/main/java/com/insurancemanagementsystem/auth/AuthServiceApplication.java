package com.insurancemanagementsystem.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.auth",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config",
    "com.insurancemanagementsystem.common.web"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.auth.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class AuthServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
