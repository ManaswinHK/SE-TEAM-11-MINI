package com.aas.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
public class RiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskModel.Band band;

    @Column(nullable = false)
    private LocalDate scoredOn;

    /** Top factors, one per line as "points|label" (AAS-F-051). */
    private String factors;

    protected RiskScore() {
    }

    public RiskScore(Long orgId, Long employeeId, RiskModel.Result result, LocalDate scoredOn) {
        this.orgId = orgId;
        this.employeeId = employeeId;
        this.score = result.score();
        this.band = result.band();
        this.scoredOn = scoredOn;
        this.factors = result.factors().stream().limit(TOP_FACTORS)
                .map(f -> f.points() + "|" + f.label())
                .collect(Collectors.joining("\n"));
    }

    public static final int TOP_FACTORS = 3;

    public List<RiskModel.Factor> topFactors() {
        if (factors == null || factors.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(factors.split("\n"))
                .map(line -> line.split("\\|", 2))
                .map(p -> new RiskModel.Factor(p[1], Integer.parseInt(p[0])))
                .toList();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getEmployeeId() { return employeeId; }
    public Integer getScore() { return score; }
    public RiskModel.Band getBand() { return band; }
    public LocalDate getScoredOn() { return scoredOn; }
}
