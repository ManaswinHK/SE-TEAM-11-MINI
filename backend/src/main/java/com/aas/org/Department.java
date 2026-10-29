package com.aas.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String name;

    private String location;

    private boolean active = true;

    protected Department() {
    }

    public Department(Long orgId, String name, String location) {
        this.orgId = orgId;
        this.name = name;
        this.location = location;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
