package com.aas.report;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Any signed-in user can subscribe to the monthly report for themselves (AAS-F-062). */
@RestController
@RequestMapping("/api/reports/subscription")
public class ReportController {

    public record Subscription(boolean subscribed) {
    }

    private final AppUserRepository users;

    public ReportController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    public Subscription get() {
        return new Subscription(me().isMonthlyReport());
    }

    @PutMapping
    @Transactional
    public Subscription set(@RequestBody Subscription req) {
        AppUser user = me();
        user.setMonthlyReport(req.subscribed());
        return new Subscription(users.save(user).isMonthlyReport());
    }

    private AppUser me() {
        CurrentUser u = CurrentUser.get();
        return users.findByIdAndOrgId(u.userId(), u.orgId()).orElseThrow();
    }
}
