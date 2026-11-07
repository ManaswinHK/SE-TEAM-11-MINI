package com.aas.employee;

import java.time.LocalDate;

/** An employee as lists of other records show them: no personal data, so nothing needs decrypting. */
public record EmployeeRef(Long id, String employeeCode, String fullName, Long departmentId, LocalDate joinDate) {
}