package com.aas.separation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/** One step of the exit clearance checklist for a separation (AAS-F-022). */
@Entity
public class ClearanceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long separationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder;

    private boolean done;
    private Long doneBy;
    private LocalDateTime doneAt;

    protected ClearanceItem() {
    }

    public ClearanceItem(Long orgId, Long separationId, String name, int sortOrder) {
        this.orgId = orgId;
        this.separationId = separationId;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void mark(boolean done, Long userId) {
        this.done = done;
        this.doneBy = done ? userId : null;
        this.doneAt = done ? LocalDateTime.now() : null;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getSeparationId() { return separationId; }
    public String getName() { return name; }
    public Integer getSortOrder() { return sortOrder; }
    public boolean isDone() { return done; }
    public Long getDoneBy() { return doneBy; }
    public LocalDateTime getDoneAt() { return doneAt; }
}
