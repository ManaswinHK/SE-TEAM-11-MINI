package com.aas.risk;

import com.aas.auth.CurrentUser;
import com.aas.risk.RiskService.RiskView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Flight-risk scores are visible to HR and to Department Managers for their own team (SO-1). */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskService service;

    public RiskController(RiskService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
    public List<RiskView> latest(@RequestParam(required = false) RiskModel.Band band,
                                 @RequestParam(required = false) Long departmentId) {
        return service.latest(CurrentUser.get(), band, departmentId);
    }

    /** Scores now instead of waiting for Monday, e.g. after a large import. */
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public Map<String, Integer> run() {
        return Map.of("scored", service.scoreOrganization(CurrentUser.get().orgId(), LocalDate.now()));
    }
}
