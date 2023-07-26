package com.sbm.application.core.converters;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;

public class LocalizedDoubleToStringConverter implements Converter<Double, String> {

	@Override
	public String convert(Double source) {
		return String.format(Locale.forLanguageTag("tr-TR"), "%.2f", source);
	}

}
