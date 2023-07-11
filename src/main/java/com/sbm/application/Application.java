package com.sbm.application;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;

import io.r2dbc.spi.ConnectionFactory;

@SpringBootApplication
public class Application {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Bean
    ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
		log.info("Database Connnection initializing...");
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
		log.info("Database Connnection initialized!");
        return initializer;
    }
	
	
}
