package com.aas.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class AttritionAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long departmentId;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;

    protected AttritionAlert() {
    }

    public AttritionAlert(Long orgId, Long departmentId, BigDecimal rate, BigDecimal threshold, LocalDate periodFrom,
                          LocalDate periodTo) {
        this.orgId = orgId;
        this.departmentId = departmentId;
        this.rate = rate;
        this.threshold = threshold;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
    }

    public void resolve() { this.resolvedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getDepartmentId() { return departmentId; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getThreshold() { return threshold; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
