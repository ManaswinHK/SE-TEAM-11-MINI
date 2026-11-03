package com.aas.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The attrition formula from the SRS: separations in period / average headcount in period x 100. */
public final class AttritionCalculator {

    private AttritionCalculator() {
    }

    public static BigDecimal averageHeadcount(int opening, int closing) {
        return BigDecimal.valueOf(opening + closing).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /** Percentage rounded to 2 decimals; 0 when there is no headcount (avoids division by zero). */
    public static BigDecimal rate(long separations, BigDecimal averageHeadcount) {
        if (averageHeadcount.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(separations).multiply(BigDecimal.valueOf(100))
                .divide(averageHeadcount, 2, RoundingMode.HALF_UP);
    }

    /** Tenure band at exit used in breakdowns (AAS-F-041). */
    public static String tenureBand(int months) {
        if (months < 12) return "< 1 year";
        if (months < 36) return "1-3 years";
        if (months < 60) return "3-5 years";
        return "> 5 years";
    }
}
