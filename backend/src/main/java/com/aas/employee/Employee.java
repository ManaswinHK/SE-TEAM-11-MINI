package com.aas.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String employeeCode;

    @Column(nullable = false)
    private String fullName;

    private String email;

    @Column(nullable = false)
    private Long departmentId;

    private String designation;
    private String managerName;
    private String location;
    private String grade;
    private String gender;

    @Column(nullable = false)
    private LocalDate joinDate;

    private Integer performanceRating;
    private LocalDate lastPromotionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    private LocalDate lastWorkingDay;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Employee() {
    }

    public Employee(Long orgId, String employeeCode, String fullName, Long departmentId, LocalDate joinDate) {
        this.orgId = orgId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.departmentId = departmentId;
        this.joinDate = joinDate;
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    /** Whether this person was on the payroll at the end of the given day (used for headcount). */
    public boolean isEmployedOn(LocalDate day) {
        return status != EmployeeStatus.INACTIVE && !joinDate.isAfter(day)
                && (lastWorkingDay == null || !lastWorkingDay.isBefore(day));
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    public Integer getPerformanceRating() { return performanceRating; }
    public void setPerformanceRating(Integer performanceRating) { this.performanceRating = performanceRating; }
    public LocalDate getLastPromotionDate() { return lastPromotionDate; }
    public void setLastPromotionDate(LocalDate lastPromotionDate) { this.lastPromotionDate = lastPromotionDate; }
    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }
    public LocalDate getLastWorkingDay() { return lastWorkingDay; }
    public void setLastWorkingDay(LocalDate lastWorkingDay) { this.lastWorkingDay = lastWorkingDay; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
