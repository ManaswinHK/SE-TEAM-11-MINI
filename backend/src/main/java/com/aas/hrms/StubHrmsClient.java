package com.aas.hrms;

import com.aas.common.CsvParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sandbox HRMS (no real HRMS is available to the project). Records come from a CSV file in the employee import
 * format, read on every sync, plus any records tests add with {@link #put}.
 */
@Component
public class StubHrmsClient implements HrmsClient {

    private final String file;
    private final Map<Long, Map<String, HrmsRecord>> added = new ConcurrentHashMap<>();
    private volatile RuntimeException nextFailure;

    public StubHrmsClient(@Value("${aas.hrms.stub-file:}") String file) {
        this.file = file;
    }

    public void put(Long orgId, HrmsRecord record) {
        added.computeIfAbsent(orgId, k -> new ConcurrentHashMap<>()).put(record.employeeCode(), record);
    }

    /** Makes the next fetch fail, to exercise failure handling. */
    public void failNextFetch(RuntimeException failure) {
        this.nextFailure = failure;
    }

    @Override
    public List<HrmsRecord> fetchEmployees(Long orgId) {
        RuntimeException failure = nextFailure;
        if (failure != null) {
            nextFailure = null;
            throw failure;
        }
        Map<String, HrmsRecord> records = new HashMap<>();
        if (!file.isBlank() && Files.exists(Path.of(file))) {
            readFile().forEach(r -> records.put(r.employeeCode(), r));
        }
        records.putAll(added.getOrDefault(orgId, Map.of()));
        return new ArrayList<>(records.values());
    }

    private List<HrmsRecord> readFile() {
        try {
            List<List<String>> rows = CsvParser.parse(Files.readString(Path.of(file), StandardCharsets.UTF_8));
            List<String> head = rows.get(0);
            List<HrmsRecord> out = new ArrayList<>();
            for (List<String> row : rows.subList(1, rows.size())) {
                Map<String, String> v = new HashMap<>();
                for (int i = 0; i < head.size() && i < row.size(); i++) {
                    v.put(head.get(i).trim(), row.get(i).trim());
                }
                out.add(new HrmsRecord(v.get("employeeCode"), v.get("fullName"), v.get("email"), v.get("department"),
                        v.get("designation"), v.get("managerName"), v.get("location"), v.get("grade"),
                        v.getOrDefault("joinDate", "").isEmpty() ? null : LocalDate.parse(v.get("joinDate"))));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the HRMS sandbox file " + file, e);
        }
    }
}
