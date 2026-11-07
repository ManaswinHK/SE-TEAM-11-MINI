package com.aas.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The flight-risk score (AAS-F-050): a points model over tenure, time since last promotion, performance rating
 * and the team's attrition, so every point can be explained (AAS-F-051). There is no historical data to train a
 * statistical model on, so the weights encode the risk patterns described in the SRS.
 */
public final class RiskModel {

    public enum Band { LOW, MEDIUM, HIGH }

    public record Factor(String label, int points) {
    }

    public record Result(int score, Band band, List<Factor> factors) {
    }

    static final int BASE = 10;

    private RiskModel() {
    }

    public static Band band(int score) {
        if (score >= 70) return Band.HIGH;
        if (score >= 40) return Band.MEDIUM;
        return Band.LOW;
    }

    /**
     * @param teamAttritionRate the department's attrition rate over the last 12 months, in percent
     */
    public static Result score(LocalDate joinDate, LocalDate lastPromotionDate, Integer performanceRating,
                               BigDecimal teamAttritionRate, LocalDate today) {
        List<Factor> factors = new ArrayList<>();
        int tenureMonths = (int) Period.between(joinDate, today).toTotalMonths();
        LocalDate lastStep = lastPromotionDate != null ? lastPromotionDate : joinDate;
        int monthsSinceStep = (int) Period.between(lastStep, today).toTotalMonths();

        if (monthsSinceStep >= 36) {
            factors.add(new Factor("No promotion for " + monthsSinceStep / 12 + " years", 25));
        } else if (monthsSinceStep >= 24) {
            factors.add(new Factor("No promotion for 2 years", 18));
        } else if (monthsSinceStep >= 12) {
            factors.add(new Factor("No promotion for over a year", 8));
        }

        if (tenureMonths < 12) {
            factors.add(new Factor("Less than a year in the company", 10));
        } else if (tenureMonths < 36) {
            factors.add(new Factor("1-3 years in the company, when people most often move", 15));
        } else if (tenureMonths < 60) {
            factors.add(new Factor("3-5 years in the company", 8));
        }

        if (performanceRating != null && performanceRating >= 4 && monthsSinceStep >= 18) {
            factors.add(new Factor("High performer without a recent promotion", 20));
        } else if (performanceRating != null && performanceRating <= 2) {
            factors.add(new Factor("Low performance rating", 15));
        }

        double rate = teamAttritionRate == null ? 0 : teamAttritionRate.doubleValue();
        String team = "Team attrition " + teamAttritionRate + "% in the last 12 months";
        if (rate >= 20) {
            factors.add(new Factor(team, 25));
        } else if (rate >= 12) {
            factors.add(new Factor(team, 15));
        } else if (rate >= 6) {
            factors.add(new Factor(team, 7));
        }

        factors.sort(Comparator.comparingInt(Factor::points).reversed());
        int score = Math.min(100, BASE + factors.stream().mapToInt(Factor::points).sum());
        return new Result(score, band(score), factors);
    }
}
