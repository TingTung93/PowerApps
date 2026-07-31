package org.commercialtracking;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.util.List;

public final class ManifestWriter {
    private static final DateTimeFormatter NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public Path write(Path root, String location, List<TrackingEvent> events) throws IOException {
        String manifestId = "IN-" + NAME.format(Instant.now()) + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return write(root, manifestId, "inbound", location, events).path;
    }

    public Result write(Path root, String manifestId, String location, List<TrackingEvent> events) throws IOException {
        return write(root, manifestId, "inbound", location, events);
    }

    public Result write(Path root, String manifestId, String type, String scope, List<TrackingEvent> events) throws IOException {
        return write(root, manifestId, type, scope, java.time.ZoneId.systemDefault().getId(), events);
    }

    public Result write(Path root, String manifestId, String type, String scope, String timeZone,
                        List<TrackingEvent> events) throws IOException {
        String safeType = "custody".equalsIgnoreCase(type) ? "custody" : "inbound";
        Instant prepared = Instant.now();
        String localPrepared = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a z")
                .withZone(java.time.ZoneId.of(timeZone)).format(prepared);
        Path dir = root.resolve("manifests").resolve(Instant.now().toString().substring(0, 4))
                .resolve(safeType);
        Files.createDirectories(dir);
        Path htmlOutput = dir.resolve(manifestId + ".html");
        Path output = dir.resolve(manifestId + ".pdf");
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Inbound Manifest</title>")
                .append("<style>body{font:12px Segoe UI,Arial;margin:24px}h1{font-size:20px}")
                .append("table{width:100%;border-collapse:collapse}th,td{border:1px solid #999;padding:6px;text-align:left}")
                .append("@media print{button{display:none}}</style></head><body>")
                .append("<button onclick=\"window.print()\">Print / Save PDF</button>")
                .append("<h1>").append("custody".equals(safeType) ? "Recipient Custody Manifest" : "Inbound Receiving Manifest")
                .append("</h1><p><b>Manifest ID:</b> ")
                .append(escape(manifestId)).append("<br><b>")
                .append("custody".equals(safeType) ? "Recipient" : "Location").append(":</b> ")
                .append(escape(scope)).append("<br><b>Prepared:</b> ").append(escape(localPrepared))
                .append("<br><b>Prepared UTC:</b> ")
                .append(escape(prepared.toString())).append("<br><b>Packages:</b> ")
                .append(events.size()).append("</p>");
        if (events.size() <= 20) {
            html.append("<table class=\"detailed\"><thead><tr><th>#</th><th>Tracking</th>")
                    .append("<th>Carrier</th><th>Location</th><th>Recorded UTC</th><th>Device</th></tr></thead><tbody>");
            int i = 1;
            for (TrackingEvent event : events) {
                html.append("<tr><td>").append(i++).append("</td><td>").append(escape(event.trackingNumber))
                        .append("</td><td>").append(escape(event.carrier)).append("</td><td>")
                        .append(escape(event.location)).append("</td><td>").append(escape(event.occurredUtc))
                        .append("</td><td>").append(escape(event.deviceId)).append("</td></tr>");
            }
            html.append("</tbody></table>");
        } else {
            html.append("<table class=\"high-density\"><thead><tr><th colspan=\"2\">Package</th>")
                    .append("<th colspan=\"2\">Package</th><th colspan=\"2\">Package</th><th colspan=\"2\">Package</th></tr></thead><tbody>");
            for (int row = 0; row < (events.size() + 3) / 4; row++) {
                html.append("<tr>");
                for (int column = 0; column < 4; column++) {
                    int index = row * 4 + column;
                    if (index < events.size()) {
                        html.append("<td>").append(index + 1).append("</td><td>")
                                .append(escape(events.get(index).trackingNumber)).append("</td>");
                    } else html.append("<td></td><td></td>");
                }
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("<p>I acknowledge the package accountability represented above.</p>")
                .append("<p>Operator/recipient signature: ____________________ Date: __________</p>")
                .append("<p>Page generated for manifest ").append(escape(manifestId)).append("</p></body></html>");
        Files.write(htmlOutput, html.toString().getBytes(StandardCharsets.UTF_8));
        List<String> pdfLines = new java.util.ArrayList<String>();
        pdfLines.add("Manifest ID: " + manifestId);
        pdfLines.add(("custody".equals(safeType) ? "Recipient: " : "Location: ") + scope);
        pdfLines.add("Prepared: " + localPrepared);
        pdfLines.add("Prepared UTC: " + prepared);
        pdfLines.add("Packages: " + events.size());
        pdfLines.add("");
        if (events.size() <= 20) {
            int number = 1;
            for (TrackingEvent event : events)
                pdfLines.add((number++) + " | " + event.trackingNumber + " | " + event.carrier + " | "
                        + event.location + " | " + event.occurredUtc + " | " + event.deviceId);
        } else {
            for (int row = 0; row < (events.size() + 3) / 4; row++) {
                StringBuilder line = new StringBuilder();
                for (int column = 0; column < 4; column++) {
                    int index = row * 4 + column;
                    if (index < events.size()) {
                        if (line.length() > 0) line.append("   ");
                        line.append(index + 1).append(" ").append(events.get(index).trackingNumber);
                    }
                }
                pdfLines.add(line.toString());
            }
        }
        pdfLines.add("");
        pdfLines.add("I acknowledge the package accountability represented above.");
        pdfLines.add("Operator/recipient signature: ____________________  Date: __________");
        PortablePdf.write(output, "custody".equals(safeType) ? "Recipient Custody Manifest" : "Inbound Receiving Manifest", pdfLines);
        if (!Boolean.getBoolean("commercialtracking.noDesktop") && Desktop.isDesktopSupported())
            Desktop.getDesktop().browse(output.toUri());
        return new Result(output, htmlOutput, sha256(Files.readAllBytes(output)));
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
        Result(Path path, Path htmlPath, String checksum) { this.path = path; this.htmlPath = htmlPath; this.checksum = checksum; }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
