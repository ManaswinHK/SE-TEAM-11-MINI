package com.aas.analytics;

import com.aas.analytics.AnalyticsService.AttritionReport;
import com.aas.analytics.AnalyticsService.BandRow;
import com.aas.analytics.AnalyticsService.ExitInsights;
import com.aas.analytics.AnalyticsService.GroupRow;
import com.aas.analytics.AnalyticsService.Kpis;
import com.aas.analytics.AnalyticsService.ReasonRow;
import com.aas.common.CsvParser;

/** Writes the dashboard figures as CSV, one section per card, using the same numbers the API returns. */
final class DashboardCsv {

    private DashboardCsv() {
    }

    static String write(AttritionReport report, ExitInsights insights) {
        StringBuilder sb = new StringBuilder();
        Kpis k = report.kpis();
        row(sb, "Section", "Item", "Value");
        row(sb, "Period", "From", k.from());
        row(sb, "Period", "To", k.to());
        row(sb, "KPI", "Attrition rate %", k.attritionRate());
        row(sb, "KPI", "Voluntary attrition %", k.voluntaryRate());
        row(sb, "KPI", "Regretted attrition %", k.regrettedRate());
        row(sb, "KPI", "Early attrition %", k.earlyAttritionRate());
        row(sb, "KPI", "Separations", k.separations());
        row(sb, "KPI", "Average headcount", k.averageHeadcount());
        row(sb, "KPI", "Opening headcount", k.openingHeadcount());
        row(sb, "KPI", "Closing headcount", k.closingHeadcount());
        sb.append("\r\n");
        groups(sb, "Department", report);
        sb.append("\r\n");
        row(sb, "Tenure band", "Separations", "Share %");
        for (BandRow b : report.byTenure()) row(sb, b.band(), b.separations(), b.share());
        sb.append("\r\n");
        row(sb, "Location", "Separations", "Average headcount", "Attrition %");
        for (GroupRow g : report.byLocation()) row(sb, g.key(), g.separations(), g.averageHeadcount(), g.attritionRate());
        sb.append("\r\n");
        row(sb, "Exit reason", "Separations", "Share %");
        for (ReasonRow r : insights.reasons()) row(sb, r.reason(), r.separations(), r.share());
        return sb.toString();
    }

    private static void groups(StringBuilder sb, String title, AttritionReport report) {
        row(sb, title, "Separations", "Average headcount", "Attrition %");
        for (GroupRow g : report.byDepartment()) row(sb, g.key(), g.separations(), g.averageHeadcount(), g.attritionRate());
    }

    private static void row(StringBuilder sb, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(CsvParser.escape(String.valueOf(cells[i])));
        }
        sb.append("\r\n");
    }
}
