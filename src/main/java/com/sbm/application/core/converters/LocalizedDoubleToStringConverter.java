package com.sbm.application.core.converters;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.springframework.core.convert.converter.Converter;

public class LocalizedDoubleToStringConverter implements Converter<Double, String> {

	@Override
	public String convert(Double source) {

		DecimalFormat df = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR")));
		df.setMaximumFractionDigits(340);
		return df.format(source);
	}

}
