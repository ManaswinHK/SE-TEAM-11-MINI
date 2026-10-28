package com.aas.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record EmployeeRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,30}", message = "must be 1-30 letters, digits, '-' or '_'") String employeeCode,
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 200) String email,
            @NotNull Long departmentId,
            @Size(max = 100) String designation,
            @Size(max = 150) String managerName,
            @Size(max = 100) String location,
            @Size(max = 30) String grade,
            @Size(max = 20) String gender,
            @NotNull @PastOrPresent LocalDate joinDate,
            @Min(1) @Max(5) Integer performanceRating,
            @PastOrPresent LocalDate lastPromotionDate) {
    }

    public record EmployeeView(Long id, String employeeCode, String fullName, String email, Long departmentId,
                               String departmentName, String designation, String managerName, String location,
                               String grade, String gender, LocalDate joinDate, Integer performanceRating,
                               LocalDate lastPromotionDate, EmployeeStatus status, LocalDate lastWorkingDay,
                               int tenureMonths) {

        static EmployeeView of(Employee e, String departmentName) {
            LocalDate end = e.getLastWorkingDay() != null && e.getLastWorkingDay().isBefore(LocalDate.now())
                    ? e.getLastWorkingDay() : LocalDate.now();
            int tenure = (int) Period.between(e.getJoinDate(), end).toTotalMonths();
            return new EmployeeView(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getEmail(), e.getDepartmentId(),
                    departmentName, e.getDesignation(), e.getManagerName(), e.getLocation(), e.getGrade(), e.getGender(),
                    e.getJoinDate(), e.getPerformanceRating(), e.getLastPromotionDate(), e.getStatus(),
                    e.getLastWorkingDay(), Math.max(tenure, 0));
        }
    }

    public record PageView<T>(List<T> content, long totalElements, int page, int size, int totalPages) {
    }
}
