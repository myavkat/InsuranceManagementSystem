package com.sbm.application;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sbm.application.core.converters.LocalizedDoubleToStringConverter;
import com.sbm.application.core.converters.LocalizedStringToDoubleConverter;

import io.r2dbc.spi.ConnectionFactory;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Bean
    ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        return initializer;
    }
	
	@Configuration
	public class WebConfig implements WebMvcConfigurer {

	    @Override
	    public void addFormatters(FormatterRegistry registry) {
	        registry.addConverter(new LocalizedStringToDoubleConverter());
	        registry.addConverter(new LocalizedDoubleToStringConverter());
	    }
	}
	
}
