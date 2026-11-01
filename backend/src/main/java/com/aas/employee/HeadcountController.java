package com.aas.employee;

import com.aas.auth.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/headcount")
public class HeadcountController {

    public record SnapshotRow(LocalDate date, Long departmentId, int headcount) {
    }

    public record MonthTotal(LocalDate date, int headcount) {
    }

    private final HeadcountService service;

    public HeadcountController(HeadcountService service) {
        this.service = service;
    }

    @GetMapping("/snapshots")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','LEADERSHIP')")
    public List<SnapshotRow> snapshots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(CurrentUser.get().orgId(), from, to).stream()
                .map(s -> new SnapshotRow(s.getSnapshotDate(), s.getDepartmentId(), s.getHeadcount())).toList();
    }

    /** Organization-wide total per snapshot date, for charts. */
    @GetMapping("/snapshots/totals")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','LEADERSHIP')")
    public List<MonthTotal> totals(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<LocalDate, Integer> totals = new TreeMap<>();
        service.history(CurrentUser.get().orgId(), from, to).forEach(s -> totals.merge(s.getSnapshotDate(), s.getHeadcount(), Integer::sum));
        return totals.entrySet().stream().map(e -> new MonthTotal(e.getKey(), e.getValue())).toList();
    }

    /** Lets an HR Admin take a snapshot immediately instead of waiting for the 1st of the month. */
    @PostMapping("/snapshots")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<SnapshotRow> takeNow(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return service.takeSnapshot(CurrentUser.get().orgId(), day).entrySet().stream()
                .map(e -> new SnapshotRow(day, e.getKey(), e.getValue())).toList();
    }
}
