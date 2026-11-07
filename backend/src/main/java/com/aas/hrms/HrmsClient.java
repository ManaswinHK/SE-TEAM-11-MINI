package com.aas.hrms;

import java.time.LocalDate;
import java.util.List;

/** The HRMS Connector from the SAD: reads employee master data from the client's HR system. */
public interface HrmsClient {

    record HrmsRecord(String employeeCode, String fullName, String email, String department, String designation,
                      String managerName, String location, String grade, LocalDate joinDate) {
    }

    List<HrmsRecord> fetchEmployees(Long orgId);
}
