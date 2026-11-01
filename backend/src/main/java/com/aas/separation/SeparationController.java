package com.aas.separation;

import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.separation.SeparationDtos.SeparationRequest;
import com.aas.separation.SeparationDtos.SeparationView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/separations")
public class SeparationController {

    private final SeparationService service;

    public SeparationController(SeparationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public SeparationView record(@Valid @RequestBody SeparationRequest req) {
        return service.record(CurrentUser.get(), req);
    }

    /** Separations whose last working day falls in the period (defaults to the current calendar year). */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
    public List<SeparationView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) Long departmentId,
                                     @RequestParam(required = false) Separation.Type type) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : start.plusYears(1).minusDays(1);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        return service.list(CurrentUser.get(), start, end, departmentId, type);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
    public SeparationView get(@PathVariable Long id) {
        return service.get(CurrentUser.get(), id);
    }
}
