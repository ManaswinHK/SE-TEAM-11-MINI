package com.aas.hrms;

import com.aas.auth.CurrentUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrms")
@PreAuthorize("hasRole('HR_ADMIN')")
public class HrmsController {

    private final HrmsSyncService service;

    public HrmsController(HrmsSyncService service) {
        this.service = service;
    }

    /** Runs the nightly sync now. */
    @PostMapping("/sync")
    public HrmsSyncLog sync() {
        return service.sync(CurrentUser.get().orgId());
    }

    @GetMapping("/sync-log")
    public List<HrmsSyncLog> log() {
        return service.recent(CurrentUser.get().orgId());
    }
}
