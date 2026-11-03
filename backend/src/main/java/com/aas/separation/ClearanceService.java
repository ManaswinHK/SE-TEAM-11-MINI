package com.aas.separation;

import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.common.AuditLog;
import com.aas.common.AuditLogRepository;
import com.aas.employee.EmployeeRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exit clearance checklist (AAS-F-022). Every separation gets the standard items when it is recorded;
 * HR ticks them off and the separation can be closed only when all of them are done.
 */
@Service
public class ClearanceService {

    public static final List<String> DEFAULT_ITEMS = List.of("Laptop and ID card returned", "System access revoked",
            "Knowledge transfer completed", "Exit interview held", "Final settlement processed");

    public record ClearanceView(Long id, String name, boolean done, LocalDateTime doneAt) {
    }

    public record Checklist(Long separationId, Separation.Status status, int done, int total, List<ClearanceView> items) {
    }

    private final ClearanceItemRepository items;
    private final SeparationRepository separations;
    private final EmployeeRepository employees;
    private final AuditLogRepository audit;
    private final AccessScope scope;

    public ClearanceService(ClearanceItemRepository items, SeparationRepository separations, EmployeeRepository employees,
                            AuditLogRepository audit, AccessScope scope) {
        this.items = items;
        this.separations = separations;
        this.employees = employees;
        this.audit = audit;
        this.scope = scope;
    }

    @Transactional
    public List<ClearanceItem> createDefaults(Separation s) {
        List<ClearanceItem> created = new ArrayList<>();
        for (int i = 0; i < DEFAULT_ITEMS.size(); i++) {
            created.add(items.save(new ClearanceItem(s.getOrgId(), s.getId(), DEFAULT_ITEMS.get(i), i + 1)));
        }
        return created;
    }

    public Checklist checklist(CurrentUser user, Long separationId) {
        return checklist(load(user, separationId));
    }

    @Transactional
    public Checklist mark(CurrentUser user, Long separationId, Long itemId, boolean done) {
        Separation s = load(user, separationId);
        requireOpen(s);
        ClearanceItem item = items.findByIdAndSeparationId(itemId, s.getId())
                .orElseThrow(() -> ApiException.notFound("Clearance item"));
        item.mark(done, user.userId());
        audit.save(new AuditLog(user.orgId(), user.userId(), user.email(), done ? "CLEARANCE_DONE" : "CLEARANCE_UNDONE",
                "SEPARATION", s.getId(), item.getName()));
        return checklist(s);
    }

    /** Closes the separation; refused while the person is still serving notice or any item is pending. */
    @Transactional
    public Checklist close(CurrentUser user, Long separationId) {
        Separation s = load(user, separationId);
        requireOpen(s);
        if (s.getStatus() != Separation.Status.COMPLETED) {
            throw ApiException.conflict("A separation can be closed only after the last working day (" + s.getLastWorkingDay() + ")");
        }
        List<String> pending = items.findBySeparationIdOrderBySortOrder(s.getId()).stream()
                .filter(i -> !i.isDone()).map(ClearanceItem::getName).toList();
        if (!pending.isEmpty()) {
            throw ApiException.conflict("Clearance is not complete. Pending: " + String.join(", ", pending));
        }
        s.close();
        audit.save(new AuditLog(user.orgId(), user.userId(), user.email(), "CLOSE", "SEPARATION", s.getId(),
                "All clearance items done"));
        return checklist(s);
    }

    private static void requireOpen(Separation s) {
        if (s.getStatus() == Separation.Status.CLOSED) {
            throw ApiException.conflict("This separation is already closed");
        }
        if (s.getStatus() == Separation.Status.WITHDRAWN) {
            throw ApiException.conflict("This resignation was withdrawn; no clearance is needed");
        }
    }

    private Separation load(CurrentUser user, Long separationId) {
        Separation s = separations.findByIdAndOrgId(separationId, user.orgId())
                .orElseThrow(() -> ApiException.notFound("Separation"));
        employees.findByIdAndOrgId(s.getEmployeeId(), user.orgId())
                .ifPresent(e -> scope.checkDepartment(user, e.getDepartmentId()));
        return s;
    }

    private Checklist checklist(Separation s) {
        List<ClearanceView> views = items.findBySeparationIdOrderBySortOrder(s.getId()).stream()
                .map(i -> new ClearanceView(i.getId(), i.getName(), i.isDone(), i.getDoneAt()))
                .toList();
        int done = (int) views.stream().filter(ClearanceView::done).count();
        return new Checklist(s.getId(), s.getStatus(), done, views.size(), views);
    }
}
