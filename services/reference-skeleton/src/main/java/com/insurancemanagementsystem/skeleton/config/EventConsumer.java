package com.insurancemanagementsystem.skeleton.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class EventConsumer {

    @Bean
    public Consumer<String> skeletonEvents() {
        return message -> log.info("Received event: {}", message);
    }
}
