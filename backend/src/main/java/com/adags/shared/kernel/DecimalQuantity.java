package com.adags.shared.kernel;

import java.math.BigDecimal;

/** Physical quantities use six decimal places; arithmetic happens in decimal, never by rounding stock. */
public final class DecimalQuantity {
    private DecimalQuantity() {}

    public static double require(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 999_999_999_999d
                || BigDecimal.valueOf(value).stripTrailingZeros().scale() > 6)
            throw new IllegalArgumentException("Quantity must be finite with at most six decimal places");
        return value;
    }

    public static boolean measured(String unit) {
        return unit != null && java.util.Set.of("G", "MG", "MCG", "KG", "ML", "L").contains(unit.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public static double requireForUnit(double value, String unit) {
        require(value);
        if (!measured(unit) && value != Math.rint(value))
            throw new IllegalArgumentException("Counted inventory units require whole quantities");
        return value;
    }

    public static String format(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

    public static boolean multiple(double value, double factor) {
        return factor > 0 && BigDecimal.valueOf(value).remainder(BigDecimal.valueOf(factor)).signum() == 0;
    }

    public static double multiply(double first, double second) {
        return require(BigDecimal.valueOf(first).multiply(BigDecimal.valueOf(second)).doubleValue());
    }

    public static double add(double first, double second) {
        return require(BigDecimal.valueOf(first).add(BigDecimal.valueOf(second)).doubleValue());
    }

    public static double subtract(double first, double second) {
        return add(first, -second);
    }

    // Discrete quantities stay integral and overflow-checked.
    public static int multiply(int first, int second) { return Math.multiplyExact(first, second); }
    public static long multiply(long first, long second) { return Math.multiplyExact(first, second); }
    public static int add(int first, int second) { return Math.addExact(first, second); }
    public static long add(long first, long second) { return Math.addExact(first, second); }
}
