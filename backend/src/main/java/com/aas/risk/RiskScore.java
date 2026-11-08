package com.aas.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;

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

    protected RiskScore() {
    }

    public RiskScore(Long orgId, Long employeeId, int score, RiskModel.Band band, LocalDate scoredOn) {
        this.orgId = orgId;
        this.employeeId = employeeId;
        this.score = score;
        this.band = band;
        this.scoredOn = scoredOn;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getEmployeeId() { return employeeId; }
    public Integer getScore() { return score; }
    public RiskModel.Band getBand() { return band; }
    public LocalDate getScoredOn() { return scoredOn; }
}
