package net.tecgurus.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;
class QuoteServiceTest {

    private final QuoteService svc = new QuoteService();



    @Test
    void compute_margin_null_returns_base() {
        BigDecimal base = new BigDecimal("100.00");
        BigDecimal result = svc.computeUnitPriceWithMargin(base, null);
        assertEquals(0, result.compareTo(new BigDecimal("100.00")));
    }

    @Test
    void compute_margin_zero_returns_base() {
        BigDecimal base = new BigDecimal("100.00");
        BigDecimal result = svc.computeUnitPriceWithMargin(base, BigDecimal.ZERO);
        assertEquals(0, result.compareTo(new BigDecimal("100.00")));
    }

    @Test
    void compute_margin_decimal_12_5_applies_correctly() {
        BigDecimal base = new BigDecimal("100");
        BigDecimal result = svc.computeUnitPriceWithMargin(base, new BigDecimal("12.5"));
        // 100 * 1.125 = 112.5
        assertEquals(0, result.compareTo(new BigDecimal("112.5000000000"))); // 10 decimales por la división
    }

    @Test
    void compute_null_unitPrice_treated_as_zero() {
        BigDecimal result = svc.computeUnitPriceWithMargin(null, new BigDecimal("10"));
        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }
}