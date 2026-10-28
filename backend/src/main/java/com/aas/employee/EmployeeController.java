package com.aas.employee;

import com.aas.auth.CurrentUser;
import com.aas.employee.EmployeeDtos.EmployeeRequest;
import com.aas.employee.EmployeeDtos.EmployeeView;
import com.aas.employee.EmployeeDtos.PageView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Employee records (AAS-F-010). HR roles maintain them; Department Managers can view their own team. */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
    public PageView<EmployeeView> search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Long departmentId,
                                         @RequestParam(required = false) EmployeeStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        return service.search(CurrentUser.get(), q, departmentId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
    public EmployeeView get(@PathVariable Long id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView create(@Valid @RequestBody EmployeeRequest req) {
        return service.create(CurrentUser.get(), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public EmployeeView update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest req) {
        return service.update(CurrentUser.get(), id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public EmployeeView deactivate(@PathVariable Long id) {
        return service.deactivate(CurrentUser.get(), id);
    }
}
