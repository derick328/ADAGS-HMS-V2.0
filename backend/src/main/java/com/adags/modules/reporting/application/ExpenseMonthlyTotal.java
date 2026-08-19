package com.adags.modules.reporting.application;

public record ExpenseMonthlyTotal(int year, int month, long totalMinor, String currencyCode) {
}
