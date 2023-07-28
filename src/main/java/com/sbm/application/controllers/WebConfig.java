package com.sbm.application.controllers;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sbm.application.core.converters.LocalizedDoubleToStringConverter;
import com.sbm.application.core.converters.LocalizedStringToDoubleConverter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	    @Override
	    public void addFormatters(FormatterRegistry registry) {
	        registry.addConverter(new LocalizedStringToDoubleConverter());
	        registry.addConverter(new LocalizedDoubleToStringConverter());
	    }
}
