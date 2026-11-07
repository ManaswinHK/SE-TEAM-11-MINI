package com.aas.risk;

import com.aas.auth.CurrentUser;
import com.aas.risk.RetentionService.ActionView;
import com.aas.risk.RetentionService.NewAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/retention-actions")
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
public class RetentionController {

    public record CompleteRequest(@Size(max = 1000) String outcome) {
    }

    private final RetentionService service;

    public RetentionController(RetentionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ActionView> list(@RequestParam(required = false) Long employeeId) {
        return service.list(CurrentUser.get(), employeeId, LocalDate.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActionView log(@Valid @RequestBody NewAction req) {
        return service.log(CurrentUser.get(), req);
    }

    @PostMapping("/{id}/complete")
    public ActionView complete(@PathVariable Long id, @Valid @RequestBody CompleteRequest req) {
        return service.complete(CurrentUser.get(), id, req.outcome());
    }
}
