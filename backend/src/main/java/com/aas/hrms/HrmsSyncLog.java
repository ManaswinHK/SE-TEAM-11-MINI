package com.aas.hrms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class HrmsSyncLog {

    public enum Status { RUNNING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RUNNING;

    private int created;
    private int updated;
    private int failed;
    private String message;

    protected HrmsSyncLog() {
    }

    public HrmsSyncLog(Long orgId) {
        this.orgId = orgId;
    }

    public void finish(Status status, int created, int updated, int failed, String message) {
        this.status = status;
        this.created = created;
        this.updated = updated;
        this.failed = failed;
        this.message = message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
        this.finishedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public Status getStatus() { return status; }
    public int getCreated() { return created; }
    public int getUpdated() { return updated; }
    public int getFailed() { return failed; }
    public String getMessage() { return message; }
}
