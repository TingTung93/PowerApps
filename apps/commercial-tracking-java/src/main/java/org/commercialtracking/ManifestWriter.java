package org.commercialtracking;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class ManifestWriter {
    private static final DateTimeFormatter NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public Path write(Path root, String location, List<TrackingEvent> events) throws IOException {
        String manifestId = "IN-" + NAME.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return write(root, manifestId, "inbound", location, events).path;
    }

    public Result write(Path root, String manifestId, String location, List<TrackingEvent> events) throws IOException {
        return write(root, manifestId, "inbound", location, events);
    }

    public Result write(Path root, String manifestId, String type, String scope, List<TrackingEvent> events)
            throws IOException {
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        return write(root, manifestId, type, scope, today, "12h", events);
    }

    public Result write(Path root, String manifestId, String type, String scope, String date, String timeFormat,
                        List<TrackingEvent> events) throws IOException {
        boolean custody = "custody".equalsIgnoreCase(type);
        String safeType = custody ? "custody" : "inbound";
        String title = custody ? "Recipient Custody Manifest" : "Inbound Receiving Manifest";
        ZoneId zone = ZoneId.systemDefault();
        Instant prepared = Instant.now();
        String preparedLocal = TimeFormat.prepared(prepared.toString(), zone, timeFormat);
        String preparedUtc = TimeFormat.utcMinute(prepared.toString());
        boolean detailed = events.size() <= 20;

        Path dir = root.resolve("manifests").resolve(Instant.now().toString().substring(0, 4)).resolve(safeType);
        Files.createDirectories(dir);
        Path htmlOutput = dir.resolve(manifestId + ".html");
        Path output = dir.resolve(manifestId + ".docx");

        QrCode headerQr = QrCode.encode(manifestId);
        String headerSvg = qrSvg(headerQr, 120);
        byte[] headerPng = headerQr.toPng(4, 2);
        List<Section> sections = sections(custody, events);

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>").append(escape(title)).append("</title>")
                .append("<style>body{font:12px Segoe UI,Arial;margin:24px}h1{font-size:20px}h2{font-size:15px;margin-top:20px}")
                .append("table{width:100%;border-collapse:collapse;margin-top:8px}")
                .append("th,td{border:1px solid #999;padding:6px;text-align:left;vertical-align:top}")
                .append(".header{display:flex;justify-content:space-between;align-items:flex-start}")
                .append("@media print{button{display:none}}</style></head><body>")
                .append("<button onclick=\"window.print()\">Print / Save</button>")
                .append("<div class=\"header\"><div><h1>").append(escape(title)).append("</h1>")
                .append("<p><b>Manifest ID:</b> ").append(escape(manifestId)).append("<br>")
                .append("<b>").append(custody ? "Recipient" : "Location").append(":</b> ").append(escape(scope)).append("<br>")
                .append("<b>Date:</b> ").append(escape(date)).append("<br>")
                .append("<b>Prepared:</b> <span class=\"prepared\">").append(escape(preparedLocal)).append("</span><br>")
                .append("<b>Prepared UTC:</b> ").append(escape(preparedUtc)).append("<br>")
                .append("<b>Packages:</b> ").append(events.size()).append("</p></div>")
                .append("<div class=\"qr\">").append(headerSvg).append("</div></div>");
        if (!detailed)
            html.append("<p><i>Per-package QR codes are omitted at high density (more than 20 packages).</i></p>");
        int number = 1;
        for (Section section : sections) {
            if (custody) html.append("<h2>").append(escape(section.name)).append("</h2>");
            number = htmlTable(html, section.events, detailed, number, zone, timeFormat);
        }
        html.append("<p>I acknowledge the package accountability represented above.</p>")
                .append("<p>Operator/recipient signature: ____________________ Date: __________</p>")
                .append("<p>Page generated for manifest ").append(escape(manifestId)).append("</p></body></html>");
        Files.write(htmlOutput, html.toString().getBytes(StandardCharsets.UTF_8));

        DocxWriter docx = new DocxWriter();
        docx.heading(title);
        docx.paragraph("Manifest ID: " + manifestId);
        docx.paragraph((custody ? "Recipient: " : "Location: ") + scope);
        docx.paragraph("Date: " + date);
        docx.paragraph("Prepared: " + preparedLocal);
        docx.paragraph("Prepared UTC: " + preparedUtc);
        docx.paragraph("Packages: " + events.size());
        List<List<DocxWriter.Cell>> headerRows = new ArrayList<List<DocxWriter.Cell>>();
        List<DocxWriter.Cell> headerRow = new ArrayList<DocxWriter.Cell>();
        headerRow.add(DocxWriter.Cell.text("Manifest QR"));
        headerRow.add(DocxWriter.Cell.image(headerPng, 120, 120));
        headerRows.add(headerRow);
        docx.table(headerRows);
        if (!detailed)
            docx.paragraph("Per-package QR codes are omitted at high density (more than 20 packages).");
        int dnum = 1;
        for (Section section : sections) {
            if (custody) docx.heading(section.name);
            dnum = docxTable(docx, section.events, detailed, dnum, zone, timeFormat);
        }
        docx.paragraph("I acknowledge the package accountability represented above.");
        docx.paragraph("Operator/recipient signature: ____________________  Date: __________");
        docx.save(output);

        if (!Boolean.getBoolean("commercialtracking.noDesktop") && Desktop.isDesktopSupported())
            Desktop.getDesktop().browse(output.toUri());
        return new Result(output, htmlOutput, sha256(Files.readAllBytes(output)));
    }

    private static int htmlTable(StringBuilder html, List<TrackingEvent> events, boolean detailed, int number,
                                 ZoneId zone, String timeFormat) {
        if (detailed) {
            html.append("<table class=\"detailed\"><thead><tr><th>#</th><th>Tracking</th><th>Carrier</th>")
                    .append("<th>Location</th><th>Recipient</th><th>Recorded</th><th>Device</th><th>QR</th>")
                    .append("</tr></thead><tbody>");
            for (TrackingEvent event : events) {
                html.append("<tr><td>").append(number++).append("</td><td>").append(escape(event.trackingNumber))
                        .append("</td><td>").append(escape(event.carrier)).append("</td><td>").append(escape(event.location))
                        .append("</td><td>").append(escape(event.recipient)).append("</td><td>")
                        .append(escape(TimeFormat.prepared(event.occurredUtc, zone, timeFormat)))
                        .append("</td><td>").append(escape(event.deviceId)).append("</td><td>")
                        .append(qrSvg(QrCode.encode(event.trackingNumber), 72)).append("</td></tr>");
            }
            html.append("</tbody></table>");
        } else {
            html.append("<table class=\"high-density\"><thead><tr>")
                    .append("<th>#</th><th>Tracking</th><th>#</th><th>Tracking</th>")
                    .append("<th>#</th><th>Tracking</th><th>#</th><th>Tracking</th></tr></thead><tbody>");
            for (int row = 0; row < (events.size() + 3) / 4; row++) {
                html.append("<tr>");
                for (int column = 0; column < 4; column++) {
                    int index = row * 4 + column;
                    if (index < events.size())
                        html.append("<td>").append(number++).append("</td><td>")
                                .append(escape(events.get(index).trackingNumber)).append("</td>");
                    else html.append("<td></td><td></td>");
                }
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        return number;
    }

    private static int docxTable(DocxWriter docx, List<TrackingEvent> events, boolean detailed, int number,
                                 ZoneId zone, String timeFormat) {
        List<List<DocxWriter.Cell>> rows = new ArrayList<List<DocxWriter.Cell>>();
        if (detailed) {
            rows.add(cells("#", "Tracking", "Carrier", "Location", "Recipient", "Recorded", "Device", "QR"));
            for (TrackingEvent event : events) {
                List<DocxWriter.Cell> row = new ArrayList<DocxWriter.Cell>();
                row.add(DocxWriter.Cell.text(String.valueOf(number++)));
                row.add(DocxWriter.Cell.text(event.trackingNumber));
                row.add(DocxWriter.Cell.text(event.carrier));
                row.add(DocxWriter.Cell.text(event.location));
                row.add(DocxWriter.Cell.text(event.recipient));
                row.add(DocxWriter.Cell.text(TimeFormat.prepared(event.occurredUtc, zone, timeFormat)));
                row.add(DocxWriter.Cell.text(event.deviceId));
                try {
                    row.add(DocxWriter.Cell.image(QrCode.encode(event.trackingNumber).toPng(4, 2), 72, 72));
                } catch (IOException ex) {
                    throw new IllegalStateException("QR rendering failed for " + event.trackingNumber, ex);
                }
                rows.add(row);
            }
        } else {
            rows.add(cells("#", "Tracking"));
            for (TrackingEvent event : events) {
                List<DocxWriter.Cell> row = new ArrayList<DocxWriter.Cell>();
                row.add(DocxWriter.Cell.text(String.valueOf(number++)));
                row.add(DocxWriter.Cell.text(event.trackingNumber));
                rows.add(row);
            }
        }
        docx.table(rows);
        return number;
    }

    private static List<DocxWriter.Cell> cells(String... headers) {
        List<DocxWriter.Cell> row = new ArrayList<DocxWriter.Cell>();
        for (String header : headers) row.add(DocxWriter.Cell.text(header));
        return row;
    }

    private static String qrSvg(QrCode qr, int px) {
        int quiet = 4;
        int dim = qr.size + quiet * 2;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(px).append("\" height=\"").append(px)
                .append("\" viewBox=\"0 0 ").append(dim).append(" ").append(dim)
                .append("\" shape-rendering=\"crispEdges\" role=\"img\">")
                .append("<rect width=\"").append(dim).append("\" height=\"").append(dim).append("\" fill=\"#ffffff\"/>")
                .append("<path fill=\"#000000\" d=\"");
        for (int r = 0; r < qr.size; r++)
            for (int c = 0; c < qr.size; c++)
                if (qr.modules[r][c])
                    svg.append("M").append(c + quiet).append(" ").append(r + quiet).append("h1v1h-1z");
        svg.append("\"/></svg>");
        return svg.toString();
    }

    private static final class Section {
        final String name;
        final List<TrackingEvent> events;
        Section(String name, List<TrackingEvent> events) { this.name = name; this.events = events; }
    }

    private static List<Section> sections(boolean custody, List<TrackingEvent> events) {
        List<Section> out = new ArrayList<Section>();
        if (!custody) { out.add(new Section("", events)); return out; }
        TreeMap<String, List<TrackingEvent>> byRecipient = new TreeMap<String, List<TrackingEvent>>();
        List<TrackingEvent> unassigned = new ArrayList<TrackingEvent>();
        for (TrackingEvent event : events) {
            String recipient = event.recipient == null ? "" : event.recipient.trim();
            if (recipient.length() == 0) { unassigned.add(event); continue; }
            List<TrackingEvent> group = byRecipient.get(recipient);
            if (group == null) { group = new ArrayList<TrackingEvent>(); byRecipient.put(recipient, group); }
            group.add(event);
        }
        for (Map.Entry<String, List<TrackingEvent>> entry : byRecipient.entrySet())
            out.add(new Section(entry.getKey(), entry.getValue()));
        if (!unassigned.isEmpty()) out.add(new Section("Unassigned", unassigned));
        return out;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(bytes);
            StringBuilder result = new StringBuilder("sha256:");
            for (byte item : value) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public static String checksum(Path path) throws IOException { return sha256(Files.readAllBytes(path)); }

    public static void open(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Manifest output file is missing.");
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(path.toUri());
    }

    public static final class Result {
        public final Path path;
        public final Path htmlPath;
        public final String checksum;
        Result(Path path, Path htmlPath, String checksum) {
            this.path = path; this.htmlPath = htmlPath; this.checksum = checksum;
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
