package com.aas.org;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class OrgDtos {

    private OrgDtos() {
    }

    public record OrgView(Long id, String name, String industry, BigDecimal riskThreshold) {
        static OrgView of(Organization o) {
            return new OrgView(o.getId(), o.getName(), o.getIndustry(), o.getRiskThreshold());
        }
    }

    public record UpdateOrgRequest(@NotBlank @Size(max = 150) String name,
                                   @Size(max = 100) String industry,
                                   @NotNull @DecimalMin("0.1") @DecimalMax("100.0") BigDecimal riskThreshold) {
    }

    public record DepartmentView(Long id, String name, String location, boolean active) {
        static DepartmentView of(Department d) {
            return new DepartmentView(d.getId(), d.getName(), d.getLocation(), d.isActive());
        }
    }

    public record DepartmentRequest(@NotBlank @Size(max = 100) String name, @Size(max = 100) String location, Boolean active) {
    }

    public record ExitReasonView(Long id, String name, ExitReason.Category category, boolean active) {
        static ExitReasonView of(ExitReason r) {
            return new ExitReasonView(r.getId(), r.getName(), r.getCategory(), r.isActive());
        }
    }

    public record ExitReasonRequest(@NotBlank @Size(max = 100) String name, @NotNull ExitReason.Category category, Boolean active) {
    }

    public record RegisterOrgRequest(@NotBlank @Size(max = 150) String organizationName,
                                     @Size(max = 100) String industry,
                                     @NotBlank @Size(max = 150) String adminName,
                                     @NotBlank @Email String adminEmail,
                                     @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                                             message = "must be at least 8 characters with letters and digits") String password) {
    }
}
