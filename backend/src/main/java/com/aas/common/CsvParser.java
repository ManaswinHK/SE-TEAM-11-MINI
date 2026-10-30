package com.aas.common;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV reader: quoted fields, escaped quotes ("") and line breaks inside quotes. */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int i = text.startsWith("﻿") ? 1 : 0; // skip Excel's UTF-8 byte-order mark

        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        row.add(field.toString());
        addIfNotBlank(rows, row);
        return rows;
    }

    /** Escapes a value for CSV output and neutralises spreadsheet formulas (AAS-SR-007). */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = startsLikeFormula(value) ? "'" + value : value;
        return v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    public static boolean startsLikeFormula(String value) {
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(s -> !s.isBlank())) {
            rows.add(row);
        }
    }
}
