package com.aas.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Something a manager will do to keep an at-risk employee, e.g. a career discussion (AAS-F-052). */
@Entity
public class RetentionAction {

    public enum Status { OPEN, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    private String outcome;
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    protected RetentionAction() {
    }

    public RetentionAction(Long orgId, Long employeeId, String action, String ownerName, LocalDate dueDate, Long createdBy) {
        this.orgId = orgId;
        this.employeeId = employeeId;
        this.action = action;
        this.ownerName = ownerName;
        this.dueDate = dueDate;
        this.createdBy = createdBy;
    }

    public boolean isOverdue(LocalDate today) {
        return status == Status.OPEN && dueDate.isBefore(today);
    }

    public void complete(String outcome) {
        this.status = Status.DONE;
        this.outcome = outcome;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getEmployeeId() { return employeeId; }
    public String getAction() { return action; }
    public String getOwnerName() { return ownerName; }
    public LocalDate getDueDate() { return dueDate; }
    public Status getStatus() { return status; }
    public String getOutcome() { return outcome; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
