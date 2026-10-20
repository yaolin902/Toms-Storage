package com.tom.storagemod.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public final class NumberUtil {
	private NumberUtil() {
	}

	private static final char[] POSTFIXES = "kMGTPE".toCharArray();
	private static final DecimalFormat ONE_DECIMAL;

	static {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		ONE_DECIMAL = new DecimalFormat("0.#");
		ONE_DECIMAL.setDecimalFormatSymbols(symbols);
		ONE_DECIMAL.setRoundingMode(RoundingMode.DOWN); // 1999 -> 1.9k, never rounds up to 2.0k
	}

	/**
	 * Compact item count for the terminal grid: 999 -> "999", 1278 -> "1.2k", 12345 -> "12k",
	 * 1500000 -> "1.5M". Never longer than 4 characters.
	 */
	public static String formatNumber(long number) {
		if (number < 1000) return Long.toString(number);
		double value = number;
		int exponent = 0;
		while (value >= 1000 && exponent < POSTFIXES.length) {
			value /= 1000;
			exponent++;
		}
		String text = value < 10 ? ONE_DECIMAL.format(value) : Long.toString((long) value);
		return text + POSTFIXES[exponent - 1];
	}
}
