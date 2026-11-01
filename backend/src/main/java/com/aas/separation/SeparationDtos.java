package com.aas.separation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class SeparationDtos {

    private SeparationDtos() {
    }

    public record SeparationRequest(@NotNull Long employeeId,
                                    @NotNull LocalDate resignationDate,
                                    @NotNull LocalDate lastWorkingDay,
                                    @NotNull Separation.Type type,
                                    @NotNull Long reasonId,
                                    boolean regretted,
                                    @Size(max = 1000) String remarks) {
    }

    public record SeparationView(Long id, Long employeeId, String employeeCode, String employeeName, Long departmentId,
                                 String departmentName, LocalDate joinDate, LocalDate resignationDate,
                                 LocalDate lastWorkingDay, int tenureMonths, Separation.Type type, Long reasonId,
                                 String reasonName, boolean regretted, Separation.Status status, String remarks) {
    }
}
