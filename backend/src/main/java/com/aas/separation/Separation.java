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
        COMPLETED,
        /** Left and every exit clearance item is done (AAS-F-022). */
        CLOSED,
        /** Resignation withdrawn during the notice period; the person stays (AAS-F-023). Not attrition. */
        WITHDRAWN
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

    private LocalDateTime closedAt;
    private LocalDateTime withdrawnAt;

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
    public LocalDateTime getClosedAt() { return closedAt; }

    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }

    /** True unless the resignation was withdrawn; withdrawn separations never count towards attrition. */
    public boolean countsAsAttrition() {
        return status != Status.WITHDRAWN;
    }

    public void close() {
        this.status = Status.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    public void withdraw(String note) {
        this.status = Status.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
        if (note != null) {
            String text = remarks == null ? "Withdrawn: " + note : remarks + "\nWithdrawn: " + note;
            this.remarks = text.length() > 1000 ? text.substring(0, 1000) : text;
        }
    }
}
