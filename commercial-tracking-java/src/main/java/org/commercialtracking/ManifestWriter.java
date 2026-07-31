package org.commercialtracking;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ManifestWriter {
    private static final DateTimeFormatter NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public Path write(Path root, String location, List<TrackingEvent> events) throws IOException {
        Path dir = root.resolve("manifests").resolve(Instant.now().toString().substring(0, 4))
                .resolve("inbound");
        Files.createDirectories(dir);
        Path output = dir.resolve("Inbound-" + NAME.format(Instant.now()) + ".html");
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Inbound Manifest</title>")
                .append("<style>body{font:12px Segoe UI,Arial;margin:24px}h1{font-size:20px}")
                .append("table{width:100%;border-collapse:collapse}th,td{border:1px solid #999;padding:6px;text-align:left}")
                .append("@media print{button{display:none}}</style></head><body>")
                .append("<button onclick=\"window.print()\">Print / Save PDF</button>")
                .append("<h1>Inbound Receiving Manifest</h1><p><b>Location:</b> ")
                .append(escape(location)).append("<br><b>Generated:</b> ")
                .append(escape(Instant.now().toString())).append("<br><b>Packages:</b> ")
                .append(events.size()).append("</p><table><thead><tr><th>#</th><th>Tracking</th>")
                .append("<th>Carrier</th><th>Location</th><th>Recorded UTC</th><th>Device</th></tr></thead><tbody>");
        int i = 1;
        for (TrackingEvent event : events) {
            html.append("<tr><td>").append(i++).append("</td><td>").append(escape(event.trackingNumber))
                    .append("</td><td>").append(escape(event.carrier)).append("</td><td>")
                    .append(escape(event.location)).append("</td><td>").append(escape(event.occurredUtc))
                    .append("</td><td>").append(escape(event.deviceId)).append("</td></tr>");
        }
        html.append("</tbody></table><p>Operator signature: ____________________ Date: __________</p></body></html>");
        Files.write(output, html.toString().getBytes(StandardCharsets.UTF_8));
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(output.toUri());
        return output;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
