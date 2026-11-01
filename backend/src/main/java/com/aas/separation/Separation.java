package com.aas.separation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Separation {

    public enum Type { VOLUNTARY, INVOLUNTARY, RETIREMENT, END_OF_CONTRACT }

    public enum Status {
        /** Resigned / notified, still working until the last working day. */
        NOTICE_PERIOD,
        /** Last working day has passed. */
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private LocalDate resignationDate;

    @Column(nullable = false)
    private LocalDate lastWorkingDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private Long reasonId;

    private boolean regretted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String remarks;
    private Long recordedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Separation() {
    }

    public Separation(Long orgId, Long employeeId, LocalDate resignationDate, LocalDate lastWorkingDay, Type type,
                      Long reasonId, boolean regretted, String remarks, Long recordedBy) {
        this.orgId = orgId;
        this.employeeId = employeeId;
        this.resignationDate = resignationDate;
        this.lastWorkingDay = lastWorkingDay;
        this.type = type;
        this.reasonId = reasonId;
        this.regretted = regretted;
        this.remarks = remarks;
        this.recordedBy = recordedBy;
        this.status = lastWorkingDay.isBefore(LocalDate.now()) ? Status.COMPLETED : Status.NOTICE_PERIOD;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getEmployeeId() { return employeeId; }
    public LocalDate getResignationDate() { return resignationDate; }
    public LocalDate getLastWorkingDay() { return lastWorkingDay; }
    public Type getType() { return type; }
    public Long getReasonId() { return reasonId; }
    public boolean isRegretted() { return regretted; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public Long getRecordedBy() { return recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
