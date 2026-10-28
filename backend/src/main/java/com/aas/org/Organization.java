package com.aas.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String industry;

    /** Attrition % above which managers are alerted (AAS-F-003, AAS-F-060). */
    @Column(nullable = false)
    private BigDecimal riskThreshold = new BigDecimal("4.00");

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Organization() {
    }

    public Organization(String name, String industry) {
        this.name = name;
        this.industry = industry;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public BigDecimal getRiskThreshold() { return riskThreshold; }
    public void setRiskThreshold(BigDecimal riskThreshold) { this.riskThreshold = riskThreshold; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
