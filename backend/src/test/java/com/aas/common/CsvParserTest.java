package com.aas.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void parsesQuotesEscapedQuotesAndLineBreaks() {
        String csv = "﻿a,b,c\r\n\"x, y\",\"say \"\"hi\"\"\",\"multi\nline\"\n\n1,,3";
        List<List<String>> rows = CsvParser.parse(csv);
        assertThat(rows).containsExactly(
                List.of("a", "b", "c"),
                List.of("x, y", "say \"hi\"", "multi\nline"),
                List.of("1", "", "3"));
    }

    /** AAS-SR-007: values that a spreadsheet would run as formulas are neutralised on export. */
    @Test
    void escapeNeutralisesFormulasAndQuotesSpecialCharacters() {
        assertThat(CsvParser.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvParser.escape("+91 98")).isEqualTo("'+91 98");
        assertThat(CsvParser.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvParser.escape("plain")).isEqualTo("plain");
        assertThat(CsvParser.escape(null)).isEmpty();
    }
}
