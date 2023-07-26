package com.sbm.application.core.helpers;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateHelper {

	public static int getYearDifferenceFromNow(String dateStr) {
		final String onlyYearExp = "^(19|20)\\d{2}$";
        final Pattern pattern = Pattern.compile(onlyYearExp, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(dateStr);
		if(matcher.find()) {
			dateStr+="-06-01";
		}
		LocalDate date = LocalDate.parse(dateStr);
		return Period.between(date, LocalDate.now()).getYears();
	}
}
