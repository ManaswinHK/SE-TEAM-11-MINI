package com.aas.employee;

import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.common.CsvParser;
import com.aas.org.Department;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk import of employees from CSV (AAS-F-011). Valid rows are imported; every invalid row is reported
 * with its row number and reason and nothing from it is saved.
 */
@Service
public class EmployeeImportService {

    public static final int MAX_ROWS = 10_000;
    public static final List<String> COLUMNS = List.of("employeeCode", "fullName", "email", "department", "designation",
            "managerName", "location", "grade", "gender", "joinDate", "performanceRating", "lastPromotionDate");
    private static final Set<String> REQUIRED = Set.of("employeeCode", "fullName", "department", "joinDate");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,30}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record RowError(int row, String employeeCode, String message) {
    }

    public record ImportResult(int totalRows, int imported, int rejected, List<RowError> errors) {
    }

    private final EmployeeRepository employees;
    private final EmployeeService employeeService;

    public EmployeeImportService(EmployeeRepository employees, EmployeeService employeeService) {
        this.employees = employees;
        this.employeeService = employeeService;
    }

    public static String template() {
        return String.join(",", COLUMNS) + "\r\n"
                + "E1001,Riya Sharma,riya.sharma@example.com,Sales,Sales Executive,Meera Iyer,Mumbai,L2,Female,2024-03-18,4,\r\n";
    }

    @Transactional
    public ImportResult importCsv(CurrentUser user, String csv) {
        List<List<String>> rows = CsvParser.parse(csv);
        if (rows.isEmpty()) {
            throw ApiException.badRequest("The file is empty");
        }
        Map<String, Integer> header = new HashMap<>();
        List<String> head = rows.get(0);
        for (int i = 0; i < head.size(); i++) {
            String name = head.get(i).trim();
            final int idx = i;
            COLUMNS.stream().filter(c -> c.equalsIgnoreCase(name)).findFirst().ifPresent(c -> header.put(c, idx));
        }
        List<String> missing = REQUIRED.stream().filter(c -> !header.containsKey(c)).sorted().toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("Missing required column(s): " + String.join(", ", missing)
                    + ". Download the template to see the expected format.");
        }
        int dataRows = rows.size() - 1;
        if (dataRows > MAX_ROWS) {
            throw ApiException.badRequest("A file can contain at most " + MAX_ROWS + " employees (found " + dataRows + ")");
        }

        Map<String, Department> deptByName = employeeService.departmentsById(user.orgId()).values().stream()
                .filter(Department::isActive)
                .collect(Collectors.toMap(d -> d.getName().toLowerCase(Locale.ROOT), d -> d, (a, b) -> a));
        Set<String> existingCodes = employees.findByOrgId(user.orgId()).stream()
                .map(e -> e.getEmployeeCode().toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(HashSet::new));

        List<RowError> errors = new ArrayList<>();
        List<Employee> valid = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, String> v = new HashMap<>();
            header.forEach((col, idx) -> v.put(col, idx < row.size() ? row.get(idx).trim() : ""));
            String code = v.get("employeeCode");
            try {
                Employee e = toEmployee(user.orgId(), v, deptByName, existingCodes);
                existingCodes.add(code.toLowerCase(Locale.ROOT));
                valid.add(e);
            } catch (IllegalArgumentException ex) {
                // Row numbers match what users see in Excel: the header is row 1.
                errors.add(new RowError(r + 1, code, ex.getMessage()));
            }
        }
        employees.saveAll(valid);
        return new ImportResult(dataRows, valid.size(), errors.size(), errors);
    }

    private Employee toEmployee(Long orgId, Map<String, String> v, Map<String, Department> deptByName, Set<String> codes) {
        for (Map.Entry<String, String> cell : v.entrySet()) {
            if (CsvParser.startsLikeFormula(cell.getValue())) {
                throw new IllegalArgumentException(cell.getKey() + " starts with '" + cell.getValue().charAt(0)
                        + "'; formulas are not allowed");
            }
        }
        for (String col : REQUIRED) {
            if (v.getOrDefault(col, "").isEmpty()) {
                throw new IllegalArgumentException(col + " is required");
            }
        }
        String code = v.get("employeeCode");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("employeeCode must be 1-30 letters, digits, '-' or '_'");
        }
        if (codes.contains(code.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("employeeCode " + code + " already exists");
        }
        Department dept = deptByName.get(v.get("department").toLowerCase(Locale.ROOT));
        if (dept == null) {
            throw new IllegalArgumentException("Unknown department '" + v.get("department") + "'");
        }
        LocalDate joined = date(v.get("joinDate"), "joinDate");
        if (joined.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("joinDate cannot be in the future");
        }
        String email = v.getOrDefault("email", "");
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email is not valid");
        }
        Integer rating = null;
        String ratingText = v.getOrDefault("performanceRating", "");
        if (!ratingText.isEmpty()) {
            try {
                rating = Integer.valueOf(ratingText);
            } catch (NumberFormatException ex) {
                rating = -1;
            }
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("performanceRating must be a whole number from 1 to 5");
            }
        }
        LocalDate promoted = null;
        String promotedText = v.getOrDefault("lastPromotionDate", "");
        if (!promotedText.isEmpty()) {
            promoted = date(promotedText, "lastPromotionDate");
            if (promoted.isBefore(joined) || promoted.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("lastPromotionDate must be between joinDate and today");
            }
        }
        String name = v.get("fullName");
        if (name.length() > 150) {
            throw new IllegalArgumentException("fullName is longer than 150 characters");
        }

        Employee e = new Employee(orgId, code, name, dept.getId(), joined);
        e.setEmail(blankToNull(email));
        e.setDesignation(limit(v.get("designation"), 100));
        e.setManagerName(limit(v.get("managerName"), 150));
        e.setLocation(v.getOrDefault("location", "").isEmpty() ? dept.getLocation() : limit(v.get("location"), 100));
        e.setGrade(limit(v.get("grade"), 30));
        e.setGender(limit(v.get("gender"), 20));
        e.setPerformanceRating(rating);
        e.setLastPromotionDate(promoted);
        return e;
    }

    private static LocalDate date(String text, String column) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(column + " must be a date in YYYY-MM-DD format");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String limit(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.length() > max) {
            throw new IllegalArgumentException("A value is longer than " + max + " characters");
        }
        return s;
    }
}
