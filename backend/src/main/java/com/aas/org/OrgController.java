package com.aas.org;

import com.aas.auth.CurrentUser;
import com.aas.org.OrgDtos.DepartmentRequest;
import com.aas.org.OrgDtos.DepartmentView;
import com.aas.org.OrgDtos.ExitReasonRequest;
import com.aas.org.OrgDtos.ExitReasonView;
import com.aas.org.OrgDtos.OrgView;
import com.aas.org.OrgDtos.RegisterOrgRequest;
import com.aas.org.OrgDtos.UpdateOrgRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Organization settings (AAS-F-003). Everyone in the organization can read them; only HR Admins change them. */
@RestController
public class OrgController {

    private final OrgService service;

    public OrgController(OrgService service) {
        this.service = service;
    }

    @PostMapping("/api/organizations/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgView register(@Valid @RequestBody RegisterOrgRequest req) {
        return OrgView.of(service.register(req));
    }

    @GetMapping("/api/org")
    public OrgView get() {
        return OrgView.of(service.get(CurrentUser.get().orgId()));
    }

    @PutMapping("/api/org")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public OrgView update(@Valid @RequestBody UpdateOrgRequest req) {
        return OrgView.of(service.update(CurrentUser.get().orgId(), req));
    }

    @GetMapping("/api/departments")
    public List<DepartmentView> departments() {
        return service.departments(CurrentUser.get().orgId()).stream().map(DepartmentView::of).toList();
    }

    @PostMapping("/api/departments")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView createDepartment(@Valid @RequestBody DepartmentRequest req) {
        return DepartmentView.of(service.createDepartment(CurrentUser.get().orgId(), req));
    }

    @PutMapping("/api/departments/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public DepartmentView updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest req) {
        return DepartmentView.of(service.updateDepartment(CurrentUser.get().orgId(), id, req));
    }

    @GetMapping("/api/exit-reasons")
    public List<ExitReasonView> exitReasons() {
        return service.exitReasons(CurrentUser.get().orgId()).stream().map(ExitReasonView::of).toList();
    }

    @PostMapping("/api/exit-reasons")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExitReasonView createExitReason(@Valid @RequestBody ExitReasonRequest req) {
        return ExitReasonView.of(service.createExitReason(CurrentUser.get().orgId(), req));
    }

    @PutMapping("/api/exit-reasons/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ExitReasonView updateExitReason(@PathVariable Long id, @Valid @RequestBody ExitReasonRequest req) {
        return ExitReasonView.of(service.updateExitReason(CurrentUser.get().orgId(), id, req));
    }
}
