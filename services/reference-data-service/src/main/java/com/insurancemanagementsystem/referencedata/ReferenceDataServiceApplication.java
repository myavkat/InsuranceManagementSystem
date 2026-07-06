package com.insurancemanagementsystem.referencedata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.referencedata",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config",
    "com.insurancemanagementsystem.common.web"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.referencedata.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class ReferenceDataServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(ReferenceDataServiceApplication.class, args);
    }
}
