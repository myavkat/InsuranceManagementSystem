package com.sbm.application.core.converters;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import org.springframework.core.convert.converter.Converter;

public class LocalizedStringToDoubleConverter implements Converter<String, Double> {

	@Override
	public Double convert(String source) {
		try {
			return NumberFormat.getInstance(Locale.forLanguageTag("tr-TR")).parse(source).doubleValue();
		} catch (ParseException e) {
			e.printStackTrace();
			throw new RuntimeException("Cannot convert String %s to Double with tr-TR locale".formatted(source));
		}
	}

}
