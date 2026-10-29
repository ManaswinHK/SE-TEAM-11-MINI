package com.aas.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ExitReason {

    public enum Category { VOLUNTARY, INVOLUNTARY, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private boolean active = true;

    protected ExitReason() {
    }

    public ExitReason(Long orgId, String name, Category category) {
        this.orgId = orgId;
        this.name = name;
        this.category = category;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
