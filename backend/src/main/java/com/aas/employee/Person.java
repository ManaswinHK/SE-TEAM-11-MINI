package com.aas.employee;

import java.time.LocalDate;

/**
 * The few employee fields headcount and attrition need. Analytics loads these instead of whole employees, so no
 * personal data is decrypted and large organizations stay fast (AAS-NF-001).
 */
public record Person(Long id, Long departmentId, LocalDate joinDate, LocalDate lastWorkingDay, EmployeeStatus status,
                     String location) {

    /** Same rule as {@link Employee#isEmployedOn}. */
    public boolean isEmployedOn(LocalDate day) {
        return status != EmployeeStatus.INACTIVE && !joinDate.isAfter(day)
                && (lastWorkingDay == null || !lastWorkingDay.isBefore(day));
    }
}
