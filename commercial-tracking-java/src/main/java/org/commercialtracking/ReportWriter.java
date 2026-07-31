package org.commercialtracking;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public final class ReportWriter {
    private static final DateTimeFormatter NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection) throws IOException {
        return write(root, type, period, timeZone, from, to, events, projection, true);
    }

    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection, boolean openPdf) throws IOException {
        return write(root, type, period, timeZone, from, to, events, projection, openPdf,
                "time|tracking|carrier|recipient|location|status|manifest|actor|device",
                "location", "occurred-asc", true);
    }

    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection, boolean openPdf, String columns,
                        String groupBy, String sortOrder, boolean includeSummary) throws IOException {
        boolean outbound = type.toLowerCase().startsWith("outbound");
        List<Row> rows = new ArrayList<Row>();
        for (TrackingEvent event : events) {
            if (!(outbound ? "PACKAGE_RELEASED".equals(event.eventType) : "PACKAGE_RECEIVED".equals(event.eventType)))
                continue;
            Instant occurred = Instant.parse(event.occurredUtc);
            if (occurred.isBefore(from) || !occurred.isBefore(to)) continue;
            PackageState state = projection.find(event.trackingNumber);
            rows.add(new Row(event, state));
        }
        Comparator<Row> occurred = Comparator.comparing(row -> row.event.occurredUtc);
        if ("occurred-desc".equals(sortOrder)) occurred = occurred.reversed();
        final Comparator<Row> occurredFinal = occurred;
        if ("location".equals(groupBy))
            Collections.sort(rows, Comparator.comparing((Row row) -> row.event.location).thenComparing(occurredFinal));
        else if ("recipient".equals(groupBy))
            Collections.sort(rows, Comparator.comparing((Row row) -> row.recipient).thenComparing(occurredFinal));
        else Collections.sort(rows, occurred);
        List<String> selectedColumns = new ArrayList<String>();
        for (String column : columns.split("\\|")) if (label(column) != null) selectedColumns.add(column);
        if (selectedColumns.isEmpty()) selectedColumns.addAll(Arrays.asList("time", "tracking", "carrier", "location", "status"));

        String safePeriod = period.toLowerCase().matches("day|week|month|custom") ? period.toLowerCase() : "custom";
        Path dir = root.resolve("reports").resolve(Instant.now().toString().substring(0, 4)).resolve(safePeriod);
        Files.createDirectories(dir);
        String stamp = NAME.format(Instant.now());
        Path csv = dir.resolve("Commercial-Tracking-" + stamp + ".csv");
        Path html = dir.resolve("Commercial-Tracking-" + stamp + ".html");
        Path pdf = dir.resolve("Commercial-Tracking-" + stamp + ".pdf");
        Files.write(csv, csv(rows, selectedColumns).getBytes(StandardCharsets.UTF_8));
        Files.write(html, html(type, timeZone, from, to, rows, selectedColumns, includeSummary).getBytes(StandardCharsets.UTF_8));
        List<String> pdfLines = new ArrayList<String>();
        pdfLines.add("Reporting Extract - not an audited manifest");
        pdfLines.add("Operational time zone: " + timeZone);
        pdfLines.add("Inclusive start UTC: " + from);
        pdfLines.add("Exclusive end UTC: " + to);
        pdfLines.add("Rows: " + rows.size());
        pdfLines.add("");
        for (Row row : rows) {
            StringBuilder line = new StringBuilder();
            for (String column : selectedColumns) {
                if (line.length() > 0) line.append(" | ");
                line.append(cell(row, column));
            }
            pdfLines.add(line.toString());
        }
        PortablePdf.write(pdf, type, pdfLines);
        if (openPdf && !Boolean.getBoolean("commercialtracking.noDesktop") && Desktop.isDesktopSupported())
            Desktop.getDesktop().browse(pdf.toUri());
        return new Result(pdf, html, csv, rows.size());
    }

    private static String csv(List<Row> rows, List<String> columns) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) value.append(',');
            value.append(csvCell(label(columns.get(i))));
        }
        value.append(",Event ID\r\n");
        for (Row row : rows) {
            for (String column : columns) value.append(csvCell(cell(row, column))).append(',');
            value.append(csvCell(row.event.eventId)).append("\r\n");
        }
        return value.toString();
    }

    private static String html(String type, String timeZone, Instant from, Instant to, List<Row> rows,
                               List<String> columns, boolean includeSummary) {
        Map<String, Integer> locations = new LinkedHashMap<String, Integer>();
        Map<String, Integer> carriers = new LinkedHashMap<String, Integer>();
        int unassigned = 0;
        StringBuilder body = new StringBuilder();
        for (Row row : rows) {
            increment(locations, row.event.location);
            increment(carriers, row.event.carrier);
            if (row.recipient.length() == 0) unassigned++;
            body.append("<tr>");
            for (String column : columns) body.append("<td>").append(escape(cell(row, column))).append("</td>");
            body.append("</tr>");
        }
        StringBuilder headings = new StringBuilder();
        for (String column : columns) headings.append("<th>").append(escape(label(column))).append("</th>");
        return "<!doctype html><meta charset=\"utf-8\"><title>Commercial Tracking Report</title>"
                + "<style>body{font:12px Segoe UI,Arial;margin:24px}table{width:100%;border-collapse:collapse}"
                + "th,td{border:1px solid #999;padding:6px;text-align:left}@media print{button{display:none}}</style>"
                + "<button onclick=\"window.print()\">Create PDF / Print</button><h1>" + escape(type)
                + "</h1><p><b>Reporting Extract — not an audited manifest.</b></p><p>Operational time zone: "
                + escape(timeZone) + "<br>Inclusive start: " + escape(from.toString()) + "<br>Exclusive end: "
                + escape(to.toString()) + "<br>Rows: " + rows.size() + "</p>"
                + (includeSummary ? "<h2>Summary</h2><p>By location: " + escape(locations.toString())
                + "<br>By carrier: " + escape(carriers.toString()) + "<br>Assigned: "
                + (rows.size() - unassigned) + " · Unassigned: " + unassigned + "</p>" : "")
                + "<table><thead><tr>" + headings + "</tr></thead><tbody>" + body + "</tbody></table>";
    }

    private static String label(String key) {
        if ("time".equals(key)) return "Occurred UTC";
        if ("tracking".equals(key)) return "Tracking Number";
        if ("carrier".equals(key)) return "Carrier";
        if ("recipient".equals(key)) return "Recipient";
        if ("location".equals(key)) return "Location";
        if ("status".equals(key)) return "Current Status";
        if ("manifest".equals(key)) return "Manifest ID";
        if ("actor".equals(key)) return "Windows Account";
        if ("device".equals(key)) return "Workstation";
        return null;
    }

    private static String cell(Row row, String key) {
        if ("time".equals(key)) return row.event.occurredUtc;
        if ("tracking".equals(key)) return row.event.trackingNumber;
        if ("carrier".equals(key)) return row.event.carrier;
        if ("recipient".equals(key)) return row.recipient;
        if ("location".equals(key)) return row.event.location;
        if ("status".equals(key)) return row.status;
        if ("manifest".equals(key)) return row.manifestId;
        if ("actor".equals(key)) return row.event.actor;
        if ("device".equals(key)) return row.event.deviceId;
        return "";
    }

    private static void increment(Map<String, Integer> values, String key) {
        String safe = key == null || key.length() == 0 ? "Unspecified" : key;
        values.put(safe, values.containsKey(safe) ? values.get(safe) + 1 : 1);
    }

    private static String csvCell(String value) {
        if (value == null) return "";
        String safe = value;
        if (safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-") || safe.startsWith("@"))
            safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final class Row {
        final TrackingEvent event;
        final String recipient;
        final String status;
        final String manifestId;
        Row(TrackingEvent event, PackageState state) {
            this.event = event;
            recipient = state == null ? event.recipient : state.recipient;
            status = state == null ? event.status : state.status;
            manifestId = state == null ? event.manifestId : state.manifestId;
        }
    }

    public static final class Result {
        public final Path pdf;
        public final Path html;
        public final Path csv;
        public final int count;
        Result(Path pdf, Path html, Path csv, int count) { this.pdf = pdf; this.html = html; this.csv = csv; this.count = count; }
    }
}
