package com.aas.report;

import com.aas.analytics.AnalyticsService;
import com.aas.analytics.AnalyticsService.AttritionReport;
import com.aas.analytics.AnalyticsService.Kpis;
import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.CurrentUser;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import com.aas.survey.MailGateway;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Monthly attrition summary by email on the 2nd of each month (AAS-F-062). Each subscriber gets the figures they
 * are allowed to see, so a Department Manager's report covers their own department.
 */
@Service
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final AnalyticsService analytics;
    private final MailGateway mail;

    public MonthlyReportService(OrganizationRepository orgs, AppUserRepository users, AnalyticsService analytics,
                                MailGateway mail) {
        this.orgs = orgs;
        this.users = users;
        this.analytics = analytics;
        this.mail = mail;
    }

    @Scheduled(cron = "${aas.report.cron:0 0 7 2 * *}")
    public void sendMonthlyReports() {
        sendMonthlyReports(LocalDate.now());
    }

    /** Sends last month's report to every subscriber if {@code today} is the 2nd; returns how many were sent. */
    public int sendMonthlyReports(LocalDate today) {
        if (today.getDayOfMonth() != 2) {
            return 0;
        }
        YearMonth month = YearMonth.from(today).minusMonths(1);
        int sent = 0;
        for (Organization org : orgs.findAll()) {
            for (AppUser u : users.findByOrgIdOrderByFullName(org.getId())) {
                if (!u.isActive() || !u.isMonthlyReport()) {
                    continue;
                }
                CurrentUser as = new CurrentUser(u.getId(), org.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getDepartmentId());
                try {
                    AttritionReport r = analytics.attrition(as, month.atDay(1), month.atEndOfMonth(), null);
                    mail.send(u.getEmail(), subject(org, month), body(org, month, r));
                    sent++;
                } catch (RuntimeException ex) {
                    log.warn("Monthly report for user {} not sent: {}", u.getId(), ex.getMessage());
                }
            }
        }
        return sent;
    }

    static String subject(Organization org, YearMonth month) {
        return org.getName() + " attrition report, " + month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    private static String body(Organization org, YearMonth month, AttritionReport r) {
        Kpis k = r.kpis();
        String departments = r.byDepartment().stream()
                .map(g -> "  %-20s %3d left, %6s%%".formatted(g.key(), g.separations(), g.attritionRate()))
                .collect(Collectors.joining("\n"));
        return """
                Attrition summary for %s, %s to %s

                Attrition rate:       %s%%  (%d left, average headcount %s)
                Voluntary attrition:  %s%%
                Regretted attrition:  %s%%
                Headcount:            %d at the start, %d at the end

                By department:
                %s

                Open the AAS dashboard for the full breakdown. You can stop these emails in Settings.
                """.formatted(org.getName(), month.atDay(1), month.atEndOfMonth(), k.attritionRate(), k.separations(),
                k.averageHeadcount(), k.voluntaryRate(), k.regrettedRate(), k.openingHeadcount(), k.closingHeadcount(),
                departments.isEmpty() ? "  (none)" : departments);
    }
}
