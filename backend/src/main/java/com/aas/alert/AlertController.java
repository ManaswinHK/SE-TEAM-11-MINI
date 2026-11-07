package com.aas.alert;

import com.aas.alert.AlertService.AlertView;
import com.aas.auth.CurrentUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    /** The in-app side of AAS-F-060: HR sees every department, Department Managers their own. */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','DEPT_MANAGER')")
    public List<AlertView> recent() {
        return service.recent(CurrentUser.get());
    }
}
