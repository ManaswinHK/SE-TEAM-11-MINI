package com.aas.survey;

import com.aas.auth.CurrentUser;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.OrganizationRepository;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exit-survey invitations (AAS-F-030). Recording a separation only creates the invite; a job that runs every
 * minute sends it, which keeps within the 5-minute limit and retries if the mail server is down.
 */
@Service
public class SurveyService {

    public record InviteStatus(Long separationId, SurveyInvite.Status status) {
    }

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SurveyInviteRepository invites;
    private final SeparationRepository separations;
    private final EmployeeRepository employees;
    private final OrganizationRepository orgs;
    private final MailGateway mail;
    private final String baseUrl;

    public SurveyService(SurveyInviteRepository invites, SeparationRepository separations, EmployeeRepository employees,
                         OrganizationRepository orgs, MailGateway mail,
                         @Value("${aas.survey.base-url:http://localhost:5173}") String baseUrl) {
        this.invites = invites;
        this.separations = separations;
        this.employees = employees;
        this.orgs = orgs;
        this.mail = mail;
        this.baseUrl = baseUrl;
    }

    /** 256 random bits, URL safe. */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** People with no email on file get no invite: there is nowhere to send it. */
    @Transactional
    public Optional<SurveyInvite> createInvite(Separation s, Employee e) {
        if (e.getEmail() == null || e.getEmail().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(invites.save(new SurveyInvite(s.getOrgId(), s.getId(), newToken(), LocalDateTime.now())));
    }

    /** The resignation was withdrawn: the link stops working and no more mail goes out. */
    @Transactional
    public void cancel(Long separationId) {
        invites.findBySeparationId(separationId).ifPresent(i -> i.cancel(LocalDateTime.now()));
    }

    @Scheduled(cron = "${aas.survey.send-cron:0 * * * * *}")
    @Transactional
    public void sendPending() {
        LocalDateTime now = LocalDateTime.now();
        for (SurveyInvite invite : invites.findBySentAtIsNullAndCancelledAtIsNullAndExpiresAtAfter(now)) {
            if (deliver(invite, "Please share why you are leaving", "We would value your honest feedback.")) {
                invite.markSent(now);
            }
        }
    }

    public List<InviteStatus> statuses(CurrentUser user) {
        LocalDateTime now = LocalDateTime.now();
        return invites.findByOrgId(user.orgId()).stream()
                .map(i -> new InviteStatus(i.getSeparationId(), i.status(now)))
                .toList();
    }

    /** A failure leaves the invite as it was, so the next run tries again. */
    private boolean deliver(SurveyInvite invite, String subject, String opening) {
        Optional<Employee> employee = separations.findById(invite.getSeparationId())
                .flatMap(s -> employees.findById(s.getEmployeeId()));
        if (employee.isEmpty() || employee.get().getEmail() == null) {
            return false;
        }
        String org = orgs.findById(invite.getOrgId()).map(o -> o.getName()).orElse("your organization");
        String body = """
                Hello %s,

                %s The survey takes about two minutes and does not need a login:

                %s/survey/%s

                The link works once and expires on %s.

                %s HR
                """.formatted(employee.get().getFullName(), opening, baseUrl, invite.getToken(),
                invite.getExpiresAt().toLocalDate(), org);
        try {
            mail.send(employee.get().getEmail(), subject, body);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not send survey email for invite {}: {}", invite.getId(), ex.getMessage());
            return false;
        }
    }
}
