package com.aas.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/** A completed exit survey: 1-5 ratings plus optional free text (AAS-F-031). */
@Entity
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long separationId;

    @Column(nullable = false)
    private Integer manager;

    @Column(nullable = false)
    private Integer pay;

    @Column(nullable = false)
    private Integer growth;

    @Column(nullable = false)
    private Integer workLife;

    @Column(nullable = false)
    private Integer culture;

    private String comments;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    protected SurveyResponse() {
    }

    public SurveyResponse(Long orgId, Long separationId, int manager, int pay, int growth, int workLife, int culture,
                          String comments, LocalDateTime submittedAt) {
        this.orgId = orgId;
        this.separationId = separationId;
        this.manager = manager;
        this.pay = pay;
        this.growth = growth;
        this.workLife = workLife;
        this.culture = culture;
        this.comments = comments;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getSeparationId() { return separationId; }
    public Integer getManager() { return manager; }
    public Integer getPay() { return pay; }
    public Integer getGrowth() { return growth; }
    public Integer getWorkLife() { return workLife; }
    public Integer getCulture() { return culture; }
    public String getComments() { return comments; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
