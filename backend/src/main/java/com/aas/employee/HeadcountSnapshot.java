package com.aas.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class HeadcountSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long departmentId;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    private int headcount;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected HeadcountSnapshot() {
    }

    public HeadcountSnapshot(Long orgId, Long departmentId, LocalDate snapshotDate, int headcount) {
        this.orgId = orgId;
        this.departmentId = departmentId;
        this.snapshotDate = snapshotDate;
        this.headcount = headcount;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getDepartmentId() { return departmentId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public int getHeadcount() { return headcount; }
    public void setHeadcount(int headcount) { this.headcount = headcount; this.createdAt = LocalDateTime.now(); }
}
