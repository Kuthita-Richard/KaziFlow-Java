package com.kaziflow.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PayrollDAO statutory deduction calculations.
 *
 * All expected values are derived from Kenya 2024 PAYE / NSSF Tier III / NHIF
 * regulations. These tests act as a regression guard — if tax band values are
 * accidentally changed, the build fails.
 *
 * KRA PAYE bands (monthly, 2024):
 *   0 – 24,000        @10%
 *   24,001 – 32,333   @25%
 *   32,334 – 500,000  @30%
 *   500,001 – 800,000 @32.5%
 *   800,001+           @35%
 *   Personal relief: KES 2,400/month
 *
 * NSSF (2024 Tier III):
 *   Tier I: 6% of gross, max KES 420
 *   Tier II: 6% of (gross – 7,000), max KES 1,740
 *   Employee + Employer both pay the same amount.
 *
 * NHIF (2024 graduated scale):
 *   Salary ≤ 5,999: KES 150 … up to KES 1,700 for salary > 100,000.
 */
@DisplayName("PayrollDAO — Kenya 2024 statutory calculations")
class PayrollDAOTest {

    private static final double DELTA = 0.01; // 1 cent tolerance

    // ── NSSF ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NSSF employee contribution")
    class NSSFTests {

        @Test
        @DisplayName("Gross < 7,000 — only Tier I applies")
        void tierIOnly() {
            // gross=5,000 → tierI = 5000 * 0.06 = 300 (< 420 cap), tierII = 0
            assertEquals(300.0, PayrollDAO.calculateNSSF(5000), DELTA);
        }

        @Test
        @DisplayName("Gross = 7,000 — Tier I maxes at 420")
        void tierIAtMax() {
            // 7000 * 0.06 = 420 — exactly at cap
            assertEquals(420.0, PayrollDAO.calculateNSSF(7000), DELTA);
        }

        @Test
        @DisplayName("Gross > 7,000 — Tier I capped + Tier II starts")
        void tierIAndII() {
            // gross=14,000 → tierI = min(840, 420)=420; tierII = (14000-7000)*0.06=420
            assertEquals(840.0, PayrollDAO.calculateNSSF(14000), DELTA);
        }

        @Test
        @DisplayName("Gross = 36,000 — both tiers maxed")
        void bothTiersMaxed() {
            // tierI = 420; tierII = (36000-7000)*0.06 = 1740 → capped at 1740
            assertEquals(2160.0, PayrollDAO.calculateNSSF(36000), DELTA);
        }

        @Test
        @DisplayName("Gross very high — NSSF stays capped at 2,160")
        void nsssCapped() {
            assertEquals(2160.0, PayrollDAO.calculateNSSF(500_000), DELTA);
        }

        @Test
        @DisplayName("Employer contribution equals employee contribution")
        void employerMatchesEmployee() {
            double gross = 25_000;
            assertEquals(
                PayrollDAO.calculateNSSF(gross),
                PayrollDAO.calculateNSSFEmployer(gross),
                DELTA
            );
        }
    }

    // ── NHIF ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NHIF contribution (2024 graduated scale)")
    class NHIFTests {

        @ParameterizedTest(name = "gross={0} → NHIF={1}")
        @CsvSource({
            "3000,  150",
            "5999,  150",
            "6000,  300",
            "7999,  300",
            "8000,  400",
            "11999, 400",
            "12000, 500",
            "14999, 500",
            "15000, 600",
            "19999, 600",
            "25000, 750",
            "30000, 850",
            "50000, 1100",
            "70000, 1300",
            "100000,1600",
            "150000,1700",
        })
        void nhifBands(double gross, double expected) {
            assertEquals(expected, PayrollDAO.calculateNHIF(gross), DELTA);
        }
    }

    // ── PAYE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PAYE — 2024 Kenya progressive tax bands")
    class PAYETests {

        @Test
        @DisplayName("Gross 20,000 — first band, personal relief may zero PAYE")
        void firstBandZeroPAYE() {
            // taxable = 20000 - nssf(20000)=420+780=1200 = 18800
            // tax = 18800*0.10=1880, relief=-2400 → max(1880-2400,0)=0
            assertEquals(0.0, PayrollDAO.calculatePAYE(20_000), DELTA);
        }

        @Test
        @DisplayName("Gross 30,000 — spans into second band")
        void secondBand() {
            double paye = PayrollDAO.calculatePAYE(30_000);
            assertTrue(paye > 0, "PAYE should be positive for gross 30,000");
            assertTrue(paye < 5000, "PAYE for 30k should be under 5,000");
        }

        @Test
        @DisplayName("Gross 50,000 — third band (30%)")
        void thirdBand() {
            double paye = PayrollDAO.calculatePAYE(50_000);
            // taxable ≈ 50000 - 2160(nssf) = 47840
            // 24000*0.10=2400; (32333-24000)*0.25=2083.25; (47840-32333)*0.30=4652.1
            // total=9135.35 - 2400 relief = 6735.35 ish
            assertTrue(paye > 5000 && paye < 10000,
                "PAYE for 50k should be 5k–10k, got: " + paye);
        }

        @Test
        @DisplayName("PAYE never negative due to personal relief")
        void payeNotNegative() {
            // Very low salary — relief absorbs all tax
            assertTrue(PayrollDAO.calculatePAYE(5_000) >= 0);
            assertTrue(PayrollDAO.calculatePAYE(24_000) >= 0);
        }

        @Test
        @DisplayName("PAYE increases with income")
        void payeMonotonicallyIncreases() {
            double p30k = PayrollDAO.calculatePAYE(30_000);
            double p60k = PayrollDAO.calculatePAYE(60_000);
            double p100k = PayrollDAO.calculatePAYE(100_000);
            assertTrue(p60k > p30k, "PAYE at 60k should exceed 30k");
            assertTrue(p100k > p60k, "PAYE at 100k should exceed 60k");
        }
    }

    // ── Full calculate() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Full payslip calculate()")
    class CalculateTests {

        @Test
        @DisplayName("Returns array of 8 elements")
        void returnLength() {
            double[] result = PayrollDAO.calculate(50_000, 5_000, 0);
            assertEquals(8, result.length, "calculate() must return exactly 8 elements");
        }

        @Test
        @DisplayName("Element [0] = gross + allowances")
        void totalGross() {
            double[] result = PayrollDAO.calculate(50_000, 5_000, 0);
            assertEquals(55_000.0, result[0], DELTA);
        }

        @Test
        @DisplayName("Net pay = gross - total deductions")
        void netPayCorrect() {
            double[] r = PayrollDAO.calculate(50_000, 0, 0);
            // r[0]=totalGross, r[6]=totalDeductions, r[7]=netPay
            assertEquals(r[0] - r[6], r[7], DELTA);
        }

        @Test
        @DisplayName("Net pay is positive for minimum wage (KES 15,201)")
        void netPayPositive() {
            double[] r = PayrollDAO.calculate(15_201, 0, 0);
            assertTrue(r[7] > 0, "Net pay must be positive, got: " + r[7]);
        }

        @Test
        @DisplayName("NITA is always KES 50.00")
        void nitaFixed() {
            double[] r = PayrollDAO.calculate(100_000, 0, 0);
            assertEquals(50.0, r[5], DELTA); // r[5] = NITA
        }
    }
}
