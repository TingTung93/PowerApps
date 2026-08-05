# Manifests — QR + DOCX + HTML, void/custody + date-scoping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the manifest PDF output with a DOCX archive, add QR codes (manifest-level + per-package) to both the on-screen HTML and the printable DOCX, scope every manifest to a single received date in host machine time, group custody manifests into recipient sections, and fix void/custody eligibility. This implements spec §3 (manifest QR + DOCX + HTML) and §4 (void / custody / date-scoping eligibility) of `docs/superpowers/specs/2026-08-04-manifest-qr-void-fix-locations-editor-design.md`.

**Architecture:** `ManifestWriter` is rewritten to emit an HTML file (QR as inline SVG) **and** a DOCX file (QR as embedded PNG) using the `DocxWriter`/`QrCode` building blocks from Plan 1 and the `TimeFormat` helper from Plan 4. The DOCX replaces the PDF; the `ManifestWriter.Result` checksum is computed over the DOCX bytes. `BrowserServer.manifest()` gains a `date` request parameter, draws eligibility from all packages by host-zone received date, rejects voided packages, allows multi-recipient custody manifests, and passes the shared `timeFormat` plus the scoped date into `ManifestWriter`. The frontend gains a pure `manifestEligibility.js` module (Node-tested) and a rewritten `ManifestWorkspace` with a date picker and recipient grouping.

**Tech Stack:** Java 8 (`javac --release 8`), pure JDK (no third-party jars). Tests are plain `main()` classes run by `build.ps1`. Frontend is React 19 + MUI 7 (Vite); frontend logic tests are plain Node scripts (`node test/*.js` via `npm test`).

## Global Constraints

- **Pure JDK only** — no third-party libraries. Build is `javac --release 8 -encoding UTF-8`.
- **Java build/test command** (run from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`. `ManifestWriterTest` is already wired into `build.ps1` (line 72–73) — no `build.ps1` change is required for this plan.
- **Frontend commands** (run from `commercial-tracking-java/frontend/`): `npm test` (Node logic tests) and `npm run build` (Vite bundle).
- **Repo root** is `F:\PowerApps`. All paths below are relative to it unless absolute.
- **Package** is `org.commercialtracking`; sources under `commercial-tracking-java/src/main/java/org/commercialtracking/`, tests under `commercial-tracking-java/src/test/java/org/commercialtracking/`.
- **Checksum:** `ManifestWriter.Result.checksum` and `ManifestWriter.checksum(path)` are computed over the **DOCX** bytes; `Result.path` is the `.docx`, `Result.htmlPath` is the `.html`.
- **Consumed shared interfaces (do not redefine):**
  - Plan 1 — `QrCode.encode(String) -> QrCode` with `public final int size;` and `public final boolean[][] modules;` (`[row][col]`, true = dark), and `public byte[] toPng(int scale, int quietModules) throws java.io.IOException`.
  - Plan 1 — `DocxWriter` fluent: `heading(String)`, `paragraph(String)`, `table(java.util.List<java.util.List<DocxWriter.Cell>> rows)`, `void save(java.nio.file.Path)`; `DocxWriter.Cell.text(String)`, `DocxWriter.Cell.image(byte[] png, int widthPx, int heightPx)`.
  - Plan 4 — `org.commercialtracking.TimeFormat.date(String instantIso, java.time.ZoneId zone)`, `.prepared(String instantIso, java.time.ZoneId zone, String timeFormat)`, `.utcMinute(String instantIso)`.
  - Plan 4 — `PackageState.receivedUtc` (ISO instant, may be empty) and package maps `receivedDate`/`receivedUtc`; shared setting `timeFormat` (`"12h"`/`"24h"`); `operationalTimeZone` removed — host time is `java.time.ZoneId.systemDefault()`.

### Dependency assumptions (must hold before this plan runs)
- Plan 1 (`QrCode`, `DocxWriter`) is committed and green.
- Plan 4 has added `PackageState.receivedUtc`, exposed `receivedDate`/`status`/`location`/`recipient`/`manifestId` on the frontend `packages` array, added the `timeFormat` shared setting, created `TimeFormat`, and removed `operationalTimeZone`. `Task 2` consumes `PackageState.receivedUtc`; `Task 1` consumes `TimeFormat`. If either is missing, compilation fails — do not stub them; block on the dependency.

---

## Task 1: ManifestWriter — DOCX + HTML + QR + date + time + custody grouping

**Files:**
- Replace: `commercial-tracking-java/src/main/java/org/commercialtracking/ManifestWriter.java`
- Replace: `commercial-tracking-java/src/test/java/org/commercialtracking/ManifestWriterTest.java`

**Interfaces:**
- Consumes: `QrCode.encode(String)`, `QrCode.toPng(int,int)`, `DocxWriter` (`heading`/`paragraph`/`table`/`save`, `Cell.text`/`Cell.image`), `TimeFormat.prepared(String, ZoneId, String)`, `TimeFormat.utcMinute(String)`.
- Produces:
  - `public Result write(Path root, String manifestId, String type, String scope, String date, String timeFormat, List<TrackingEvent> events)` — the new full signature (**drops** `timeZone`; **adds** `date` yyyy-MM-dd and `timeFormat` `"12h"`/`"24h"`).
  - Convenience overloads: `write(Path, String location, List<TrackingEvent>) -> Path`; `write(Path, String manifestId, String location, List<TrackingEvent>) -> Result`; `write(Path, String manifestId, String type, String scope, List<TrackingEvent>) -> Result` (defaults date = today host-zone, timeFormat = `"12h"`).
  - `Result` unchanged in shape: `public final Path path` (`.docx`), `public final Path htmlPath` (`.html`), `public final String checksum` (over the DOCX). `checksum(Path)` and `open(Path)` retained.

- [ ] **Step 1: Write the failing test (replace `ManifestWriterTest.java`)**

Replace the entire file with:

```java
package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ManifestWriterTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("commercialtracking.noDesktop", "true");
        Path root = Files.createTempDirectory("commercial-manifest-test-");
        verify(root, 1);
        verify(root, 20);
        verify(root, 21);
        verify(root, 50);
        verify(root, 100);
        custodyGrouping(root);
        timeFormatting(root);
        System.out.println("ManifestWriterTest: PASS");
    }

    private static void verify(Path root, int count) throws Exception {
        boolean detailed = count <= 20;
        String id = "IN-TEST-" + count;
        ManifestWriter.Result result = new ManifestWriter()
                .write(root, id, "inbound", "Main Receiving", "2026-07-15", "24h", events(count));

        String html = new String(Files.readAllBytes(result.htmlPath), StandardCharsets.UTF_8);
        check(html.contains("class=\"" + (detailed ? "detailed" : "high-density") + "\""), count + " layout class");
        check(html.contains(id), "manifest identity in html");
        check(html.contains("signature"), "signature certification");
        check(html.contains("2026-07-15"), "scoped date in html");
        check(html.contains("<svg"), "header QR svg present");
        int svg = countOccurrences(html, "<svg");
        check(svg == (detailed ? count + 1 : 1), "html svg count = " + svg + " for " + count);
        if (!detailed) check(html.contains("omitted at high density"), "high-density QR omission note");

        check(result.path.toString().endsWith(".docx"), "result path is .docx");
        try (ZipFile zip = new ZipFile(result.path.toFile())) {
            check(zip.getEntry("word/document.xml") != null, "docx document part");
            int media = countMedia(zip);
            check(media == (detailed ? count + 1 : 1), "docx media count = " + media + " for " + count);
            String doc = new String(readAll(zip, zip.getEntry("word/document.xml")), StandardCharsets.UTF_8);
            check(doc.contains("2026-07-15"), "scoped date in docx");
            check(doc.contains(id), "manifest identity in docx");
        }
        check(result.checksum.equals(ManifestWriter.checksum(result.path)), "checksum computed over docx");
    }

    private static void custodyGrouping(Path root) throws Exception {
        List<TrackingEvent> mixed = new ArrayList<TrackingEvent>();
        mixed.add(evt("CU-1", "Bravo"));
        mixed.add(evt("CU-2", "Alpha"));
        mixed.add(evt("CU-3", ""));
        mixed.add(evt("CU-4", "Alpha"));
        ManifestWriter.Result custody = new ManifestWriter()
                .write(root, "CU-TEST", "custody", "All recipients", "2026-07-15", "12h", mixed);
        String html = new String(Files.readAllBytes(custody.htmlPath), StandardCharsets.UTF_8);
        check(html.contains("Recipient Custody Manifest"), "custody heading");
        check(html.contains("<h2>Alpha</h2>"), "Alpha section heading");
        check(html.contains("<h2>Bravo</h2>"), "Bravo section heading");
        check(html.contains("<h2>Unassigned</h2>"), "Unassigned section heading");
        int alpha = html.indexOf("<h2>Alpha</h2>");
        int bravo = html.indexOf("<h2>Bravo</h2>");
        int unassigned = html.indexOf("<h2>Unassigned</h2>");
        check(alpha < bravo && bravo < unassigned, "recipients alphabetical, Unassigned last");
        try (ZipFile zip = new ZipFile(custody.path.toFile())) {
            String doc = new String(readAll(zip, zip.getEntry("word/document.xml")), StandardCharsets.UTF_8);
            check(doc.contains("Unassigned"), "docx custody Unassigned section");
            check(doc.contains("Alpha") && doc.contains("Bravo"), "docx custody recipient sections");
        }
    }

    private static void timeFormatting(Path root) throws Exception {
        // TimeFormat (Plan 4) renders 12h with AM/PM, 24h without, and drops seconds.
        String p12 = prepared(new String(Files.readAllBytes(new ManifestWriter()
                .write(root, "IN-12H", "inbound", "Main Receiving", "2026-07-15", "12h", events(1)).htmlPath),
                StandardCharsets.UTF_8));
        String p24 = prepared(new String(Files.readAllBytes(new ManifestWriter()
                .write(root, "IN-24H", "inbound", "Main Receiving", "2026-07-15", "24h", events(1)).htmlPath),
                StandardCharsets.UTF_8));
        check(p12.contains("AM") || p12.contains("PM"), "12h prepared line has meridiem: " + p12);
        check(!(p24.contains("AM") || p24.contains("PM")), "24h prepared line has no meridiem: " + p24);
        check(!p12.matches(".*\\d:\\d\\d:\\d\\d.*"), "12h prepared line has no seconds: " + p12);
        check(!p24.matches(".*\\d:\\d\\d:\\d\\d.*"), "24h prepared line has no seconds: " + p24);
    }

    private static List<TrackingEvent> events(int count) {
        List<TrackingEvent> values = new ArrayList<TrackingEvent>();
        for (int i = 0; i < count; i++) values.add(evt("TRACKING-" + String.format("%04d", i), ""));
        return values;
    }

    private static TrackingEvent evt(String tracking, String recipient) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_RECEIVED";
        event.trackingNumber = tracking;
        event.carrier = "Carrier";
        event.location = "Main Receiving";
        event.recipient = recipient;
        event.deviceId = "WS-TEST";
        return event;
    }

    private static String prepared(String html) {
        String marker = "class=\"prepared\">";
        int a = html.indexOf(marker);
        if (a < 0) throw new AssertionError("prepared span missing");
        a += marker.length();
        return html.substring(a, html.indexOf("</span>", a));
    }

    private static int countOccurrences(String value, String sub) {
        int n = 0, i = 0;
        while ((i = value.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    private static int countMedia(ZipFile zip) {
        int n = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("word/media/image") && name.endsWith(".png")) n++;
        }
        return n;
    }

    private static byte[] readAll(ZipFile zip, ZipEntry entry) throws Exception {
        java.io.InputStream in = zip.getInputStream(entry);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        return out.toByteArray();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run (from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`
Expected: **Test compilation failed** — the new 7-arg `write(...)` signature and `.docx` behavior do not exist yet (the old `ManifestWriter` returns a PDF and has no `date`/`timeFormat` parameter).

- [ ] **Step 3: Write the implementation (replace `ManifestWriter.java`)**

Replace the entire file with:

```java
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

    // ---- convenience overloads ----

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

    // ---- full signature ----

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

        // ---- HTML (on-screen; QR as inline SVG) ----
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

        // ---- DOCX (archived/printable; QR as embedded PNG) ----
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

    // ---- rendering helpers ----

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

    /** Inline SVG of a QR matrix (~px square) with a 4-module quiet border; dark modules as a single path. */
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

    /** Inbound = one unnamed section; custody = recipients alphabetical, Unassigned last. */
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

    // ---- checksum / IO ----

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
```

Notes for the executor:
- `QrCode.encode(...).toPng(4, 2)` throws `java.io.IOException` (declared on `toPng`); the header PNG is generated in the top-level `write` (which declares `throws IOException`), while per-package PNGs are generated inside `docxTable` (no `throws`) so the checked exception is wrapped in `IllegalStateException`.
- Density (`detailed`) uses the **total** `events.size()`, so every custody section renders in the same layout even when an individual section is small.
- The old 6-arg `write(..., String timeZone, ...)` overload is **removed**; `BrowserServer.manifest()` (Task 2) is updated to the 7-arg call in the same branch of work. There are no other callers (verified: only `BrowserServer.manifest()` and `ManifestWriterTest` call `write`).

- [ ] **Step 4: Run to verify it passes**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: build proceeds past compilation and prints `ManifestWriterTest: PASS`.

If `ManifestWriter.java` no longer references `PortablePdf`, the `PortablePdf`/`PortablePdfTest` classes still exist and their test (`build.ps1` line 82–83) still runs — leave them untouched (their retirement belongs to the reports plan).

- [ ] **Step 5: Manual Word/scan check**

Add a temporary `System.out.println(result.path)` at the end of `verify` for `count == 3`, run the build, open the printed `.docx` in Microsoft Word: confirm the heading, header info block, the embedded manifest QR, and the per-package QR column render, and that scanning the header QR decodes to the manifest ID (`IN-TEST-3`) and a row QR to `TRACKING-0000`. Remove the temporary print line afterward.

- [ ] **Step 6: Commit**

```bash
git add commercial-tracking-java/src/main/java/org/commercialtracking/ManifestWriter.java \
        commercial-tracking-java/src/test/java/org/commercialtracking/ManifestWriterTest.java
git commit -m "feat(manifest): DOCX+HTML output with QR codes, date header, and custody recipient grouping"
```

---

## Task 2: BrowserServer.manifest() — void guard, date scoping, custody eligibility, host time

**Files:**
- Modify: `commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java` — the `manifest(...)` method (~line 474–553), `manifestMaps()` (~line 168–192), and two new private helpers.

**Interfaces:**
- Consumes: `Projection.all()`, `PackageState.receivedUtc` (Plan 4), `SharedConfigManager` `timeFormat`, `ManifestWriter.write(root, manifestId, type, scope, date, timeFormat, events)` (Task 1).
- Produces: a `date` request param (`yyyy-MM-dd`, default today host-zone), void rejection, all-package date-scoped eligibility, multi-recipient custody, and a date-labelled manifest register entry.

There is no test class for `BrowserServer` in this repo (it is exercised via the running app and the frontend). Verification for this task is compilation + the manual smoke checks in Step 5. Do **not** add a JUnit-style harness.

- [ ] **Step 1: Add the two private helpers**

Add these two methods to `BrowserServer` (place them next to `findEvent(...)`, ~line 555):

```java
    private boolean onManifestOfType(String tracking, String type) {
        for (TrackingEvent event : events) {
            if ("MANIFEST_PREPARED".equals(event.eventType)
                    && tracking.equalsIgnoreCase(event.trackingNumber)
                    && type.equals(event.parserSource)) return true;
        }
        return false;
    }

    private static String hostReceivedDate(PackageState state) {
        if (state.receivedUtc == null || state.receivedUtc.length() == 0) return "";
        try {
            return java.time.Instant.parse(state.receivedUtc)
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }
```

`ZoneId` is already imported in `BrowserServer` (used at the old line ~535). `java.time.Instant`/`java.time.LocalDate` are referenced fully-qualified below to avoid touching the import block.

- [ ] **Step 2: Replace the `manifest(...)` method body**

Replace the entire method (from `private synchronized Map<String, Object> manifest(Map<String, String> request) throws IOException {` through its closing brace, ~line 474–553) with:

```java
    private synchronized Map<String, Object> manifest(Map<String, String> request) throws IOException {
        requireConfigured();
        String type = value(request, "type", "inbound").toLowerCase();
        if (!"inbound".equals(type) && !"custody".equals(type)) throw new BadRequest("Invalid manifest type.");
        String date = value(request, "date", java.time.LocalDate.now(ZoneId.systemDefault()).toString());
        try { java.time.LocalDate.parse(date); }
        catch (RuntimeException ex) { throw new BadRequest("Manifest date must be a valid yyyy-MM-dd value."); }
        String requestedLocation = value(request, "location", "");
        String requestedRecipient = value(request, "recipient", "");
        String requested = value(request, "trackingNumbers", "");
        List<PackageState> targets = new ArrayList<PackageState>();
        if (requested.length() > 0) {
            for (String tracking : requested.split("\\|")) {
                PackageState state = projection.find(tracking);
                if (state == null) throw new BadRequest("Package not found: " + tracking);
                if ("VOIDED".equals(state.status))
                    throw new BadRequest(state.trackingNumber + " is voided and cannot be added to a manifest.");
                targets.add(state);
            }
        } else {
            for (PackageState state : projection.all()) {
                if ("VOIDED".equals(state.status)) continue;
                if (!date.equals(hostReceivedDate(state))) continue;
                if (onManifestOfType(state.trackingNumber, type)) continue;
                if ("inbound".equals(type)) {
                    if (requestedLocation.length() > 0 && !requestedLocation.equals(state.location)) continue;
                } else {
                    if (!"READY_FOR_PICKUP".equals(state.status) && !"PICKED_UP".equals(state.status)) continue;
                    if (requestedRecipient.length() > 0 && !requestedRecipient.equals(state.recipient)) continue;
                }
                targets.add(state);
            }
        }
        if (targets.isEmpty()) throw new BadRequest("No eligible packages were selected.");
        if (targets.size() > 100) throw new BadRequest("An audited manifest is limited to 100 packages. Split the selection into smaller batches.");
        for (PackageState state : targets) {
            if (onManifestOfType(state.trackingNumber, type))
                throw new BadRequest(state.trackingNumber + " is already assigned to an audited " + type + " manifest.");
        }
        String scope;
        if ("custody".equals(type)) {
            for (PackageState state : targets)
                if (!"READY_FOR_PICKUP".equals(state.status) && !"PICKED_UP".equals(state.status))
                    throw new BadRequest(state.trackingNumber + " is not eligible for a custody manifest (status " + state.status + ").");
            scope = requestedRecipient.length() > 0 ? requestedRecipient : "All recipients";
        } else {
            scope = targets.get(0).location;
            for (PackageState state : targets)
                if (!scope.equals(state.location))
                    throw new BadRequest("Inbound manifests cannot combine locations in this release. Filter the selection to one location.");
        }
        String manifestId = value(request, "manifestId", "");
        if (manifestId.length() == 0) manifestId = "MNF-"
                + Instant.now().toString().replaceAll("[^0-9]", "").substring(0, 14)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        if (!manifestId.matches("MNF-[0-9]{14}-[A-Z0-9]{6}")) throw new BadRequest("Invalid proposed manifest ID.");
        for (TrackingEvent event : events)
            if (manifestId.equals(event.manifestId)) throw new BadRequest("The proposed manifest ID is already finalized.");
        String scopeLabel = date + " · " + scope;
        List<TrackingEvent> membership = new ArrayList<TrackingEvent>();
        for (PackageState current : targets) {
            TrackingEvent source = findEvent(current.lastEventId);
            if (source == null) throw new BadRequest("Package history is incomplete for " + current.trackingNumber);
            TrackingEvent row = new TrackingEvent();
            row.trackingNumber = current.trackingNumber;
            row.carrier = current.carrier;
            row.location = current.location;
            row.recipient = current.recipient;
            row.occurredUtc = current.lastEventUtc;
            row.deviceId = current.lastDevice;
            membership.add(row);
            TrackingEvent prepared = manualEvent("MANIFEST_PREPARED", current, current.recipient,
                    "Included package event " + current.lastEventId);
            prepared.manifestId = manifestId;
            prepared.referenceEventId = current.lastEventId;
            prepared.parserSource = type;
            prepared.addressee = scopeLabel;
            store.append(prepared);
        }
        String timeFormat = "12h";
        if (sharedConfig != null) {
            String configured = sharedConfig.reload().values.get("timeFormat");
            if (configured != null && configured.length() > 0) timeFormat = configured;
        }
        ManifestWriter.Result output = new ManifestWriter().write(store.getSharedRoot(), manifestId,
                type, scope, date, timeFormat, membership);
        PackageState first = targets.get(0);
        TrackingEvent printed = manualEvent("MANIFEST_PRINTED", first, first.recipient, output.checksum);
        printed.manifestId = manifestId;
        printed.referenceEventId = first.lastEventId;
        printed.parserSource = type;
        printed.addressee = scopeLabel;
        printed.address = store.getSharedRoot().relativize(output.path).toString();
        store.append(printed);
        reload();
        Map<String, Object> response = message("Manifest created.");
        response.put("manifestId", manifestId);
        response.put("fileName", output.path.getFileName().toString());
        response.put("checksum", output.checksum);
        return response;
    }
```

Key changes vs. the original:
- `date` param added, validated as a real `yyyy-MM-dd`.
- Explicit `trackingNumbers` path now **rejects** any `VOIDED` package.
- Auto-select now iterates `projection.all()` (not `sessionPackageMaps()`), filters by host-zone received date, skips `VOIDED`, skips packages already on a manifest **of this type**, applies the optional location (inbound) / recipient (custody) filter, and for custody requires status ∈ {`READY_FOR_PICKUP`, `PICKED_UP`}.
- The per-type "already manifested" guard is retained (now via `onManifestOfType`) and still throws for explicit selections.
- The old "all same recipient + READY_FOR_PICKUP" custody restriction is removed; custody scope becomes the requested recipient or `"All recipients"`, and grouping is handled by `ManifestWriter`.
- Membership events are freshly built from `PackageState` (so `ManifestWriter` grouping/display always sees the projected recipient/location/carrier/time), not the raw source event.
- `operationalTimeZone` is gone; the shared `timeFormat` and the scoped `date` are passed into `ManifestWriter`. `addressee` stores `"<date> · <scope>"` for the register label.

- [ ] **Step 3: Update `manifestMaps()` to show the date-labelled scope**

In `manifestMaps()` (~line 177), replace the single `item.put("location", ...)` line:

```java
                item.put("location", "custody".equals(event.parserSource) ? event.addressee : event.location);
```

with:

```java
                item.put("location", event.addressee != null && event.addressee.length() > 0
                        ? event.addressee : event.location);
```

Because `addressee` now holds `"<date> · <scope>"` for both inbound and custody manifests created by the new code (and held the plain scope in older records), the register's Location/recipient column shows the scoped date plus location/recipient. The fallback to `event.location` keeps pre-existing inbound records rendering.

- [ ] **Step 4: Compile**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: **Main compilation** succeeds and all wired tests (including `ManifestWriterTest: PASS`) pass. There is no `BrowserServer` unit test; a clean compile plus the unchanged test suite passing is the gate here.

- [ ] **Step 5: Manual smoke check (running app)**

Build and run the app (`powershell -File build.ps1` then launch the produced jar per `run-commercial-tracking.cmd`), then via the UI:
1. Scan an inbound package, void it, open Prepare manifest → confirm the voided package is **not** eligible and cannot be finalized.
2. Receive two packages under different recipients today, release one, open a custody manifest for today with no recipient filter → confirm both appear grouped by recipient (Unassigned last if any) and the DOCX opens.
3. Change the date picker to a day with no receipts → confirm eligibility is empty.

(If the app cannot be launched in this environment, note it and rely on the compile + `ManifestWriterTest` coverage; the frontend eligibility logic is unit-tested in Task 3.)

- [ ] **Step 6: Commit**

```bash
git add commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java
git commit -m "feat(manifest): date-scoped eligibility, void guard, multi-recipient custody, host-time formatting"
```

---

## Task 3: Frontend manifest eligibility module + Node test

**Files:**
- Create: `commercial-tracking-java/frontend/src/manifestEligibility.js`
- Create: `commercial-tracking-java/frontend/test/manifestEligibility.test.js`
- Modify: `commercial-tracking-java/frontend/package.json` (append the new test to the `test` chain)

**Interfaces:**
- Produces (pure, Node-importable): `inboundEligible(packages, {date, location})`, `custodyEligible(packages, {date, recipient})`, `groupByRecipient(list)`.

- [ ] **Step 1: Write the failing test**

Create `frontend/test/manifestEligibility.test.js`:

```js
import assert from 'node:assert/strict'
import { inboundEligible, custodyEligible, groupByRecipient } from '../src/manifestEligibility.js'

const packages = [
  { trackingNumber: 'A', status: 'READY_FOR_PICKUP', receivedDate: '2026-08-04', location: 'Dock 1', recipient: 'Bob', manifestId: '' },
  { trackingNumber: 'B', status: 'VOIDED',           receivedDate: '2026-08-04', location: 'Dock 1', recipient: 'Bob', manifestId: '' },
  { trackingNumber: 'C', status: 'READY_FOR_PICKUP', receivedDate: '2026-08-03', location: 'Dock 1', recipient: 'Al',  manifestId: '' },
  { trackingNumber: 'D', status: 'PICKED_UP',        receivedDate: '2026-08-04', location: 'Dock 2', recipient: '',    manifestId: '' },
  { trackingNumber: 'E', status: 'READY_FOR_PICKUP', receivedDate: '2026-08-04', location: 'Dock 1', recipient: 'Al',  manifestId: 'MNF-1' }
]

// Inbound: date match + location match, exclude VOIDED and already-manifested.
assert.deepEqual(
  inboundEligible(packages, { date: '2026-08-04', location: 'Dock 1' }).map(p => p.trackingNumber),
  ['A'], 'inbound date+location')
assert.deepEqual(
  inboundEligible(packages, { date: '2026-08-04', location: '' }).map(p => p.trackingNumber).sort(),
  ['A', 'D'], 'inbound any location')

// Custody: READY_FOR_PICKUP or PICKED_UP on the date, exclude VOIDED; manifestId is ignored (cross-type).
assert.deepEqual(
  custodyEligible(packages, { date: '2026-08-04', recipient: '' }).map(p => p.trackingNumber).sort(),
  ['A', 'D', 'E'], 'custody date, any recipient')
assert.deepEqual(
  custodyEligible(packages, { date: '2026-08-04', recipient: 'Al' }).map(p => p.trackingNumber),
  ['E'], 'custody recipient filter')

// Grouping: recipients alphabetical, Unassigned last.
const groups = groupByRecipient(custodyEligible(packages, { date: '2026-08-04', recipient: '' }))
assert.deepEqual(groups.map(g => g.recipient), ['Al', 'Bob', 'Unassigned'], 'group order')
assert.deepEqual(groups.find(g => g.recipient === 'Unassigned').items.map(p => p.trackingNumber), ['D'], 'unassigned bucket')

console.log('ManifestEligibilityTest: PASS')
```

- [ ] **Step 2: Run to verify it fails**

Run (from `commercial-tracking-java/frontend/`): `node test/manifestEligibility.test.js`
Expected: an error — cannot resolve `../src/manifestEligibility.js` (module does not exist yet).

- [ ] **Step 3: Write the implementation**

Create `frontend/src/manifestEligibility.js`:

```js
// Pure, Node-testable manifest eligibility derived from the `packages` array
// (which carries receivedDate, status, location, recipient, manifestId).
// The backend re-derives and validates independently; this drives the UI only.

export function inboundEligible(packages, { date, location }) {
  return packages.filter(pkg =>
    pkg.status !== 'VOIDED' &&
    !pkg.manifestId &&
    pkg.receivedDate === date &&
    (!location || pkg.location === location))
}

export function custodyEligible(packages, { date, recipient }) {
  return packages.filter(pkg =>
    (pkg.status === 'READY_FOR_PICKUP' || pkg.status === 'PICKED_UP') &&
    pkg.receivedDate === date &&
    (!recipient || pkg.recipient === recipient))
}

export function groupByRecipient(list) {
  const groups = new Map()
  const unassigned = []
  for (const pkg of list) {
    const recipient = (pkg.recipient || '').trim()
    if (!recipient) { unassigned.push(pkg); continue }
    if (!groups.has(recipient)) groups.set(recipient, [])
    groups.get(recipient).push(pkg)
  }
  const sorted = [...groups.keys()]
    .sort((a, b) => a.localeCompare(b))
    .map(recipient => ({ recipient, items: groups.get(recipient) }))
  if (unassigned.length) sorted.push({ recipient: 'Unassigned', items: unassigned })
  return sorted
}
```

Note: `custodyEligible` intentionally does **not** filter by `manifestId` — a package that was on an inbound manifest can still need a custody manifest, and the backend's per-type guard is authoritative. `inboundEligible` keeps the `!manifestId` exclusion to match the existing inbound UI behavior.

- [ ] **Step 4: Append the test to the `npm test` chain**

In `frontend/package.json`, change the `test` script from:

```json
    "test": "node test/scannerCapture.test.js"
```

to:

```json
    "test": "node test/scannerCapture.test.js && node test/manifestEligibility.test.js"
```

(If the settings/time plan has already appended its own test to this chain, keep that entry and append `&& node test/manifestEligibility.test.js` after it — do not remove other tests.)

- [ ] **Step 5: Run to verify it passes**

Run (from `commercial-tracking-java/frontend/`): `npm test`
Expected output includes `ScannerCaptureTest: PASS` and `ManifestEligibilityTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add commercial-tracking-java/frontend/src/manifestEligibility.js \
        commercial-tracking-java/frontend/test/manifestEligibility.test.js \
        commercial-tracking-java/frontend/package.json
git commit -m "feat(ui): pure manifest eligibility helpers with date/void/custody rules and tests"
```

---

## Task 4: Frontend ManifestWorkspace — date picker + recipient grouping

**Files:**
- Modify: `commercial-tracking-java/frontend/src/main.jsx` — add the eligibility import (top of file), a `localDateToday` helper, and replace the `ManifestWorkspace` component (~line 506–534).

**Interfaces:**
- Consumes: `inboundEligible`/`custodyEligible`/`groupByRecipient` (Task 3), `api.manifest` (via the `onFinalize` prop already wired at ~line 307), `formatDate` (already imported in `main.jsx`).
- Produces: a date-scoped, recipient-grouped prepare-manifest UI that sends `{ type, date, location, recipient, manifestId, trackingNumbers }` on finalize.

Do **not** touch `SettingsWorkspace` or `ReportsWorkspace`.

- [ ] **Step 1: Add the eligibility import**

After the existing icon imports in `main.jsx` (the import block ending ~line 28, before the `@fontsource` CSS imports at ~line 29), add:

```jsx
import { inboundEligible, custodyEligible, groupByRecipient } from './manifestEligibility.js'
```

- [ ] **Step 2: Add a local-date helper**

Immediately **above** the `function ManifestWorkspace(...)` definition (~line 506), add:

```jsx
function localDateToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
```

(This uses the operator's local calendar date, unlike `new Date().toISOString().slice(0,10)` which is UTC-based and can be off by a day near midnight.)

- [ ] **Step 3: Replace the `ManifestWorkspace` component**

Replace the entire `function ManifestWorkspace({ ... }) { ... }` (~line 506–534) with:

```jsx
function ManifestWorkspace({ manifests, packages, location, onFinalize, onReprint }) {
  const [tab, setTab] = useState(0)
  const [type, setType] = useState('inbound')
  const [date, setDate] = useState(localDateToday)
  const [manifestLocation, setManifestLocation] = useState(location || '')
  const [custodyRecipient, setCustodyRecipient] = useState('')
  const locations = useMemo(() => [...new Set(packages.map(item => item.location).filter(Boolean))], [packages])
  const custodyRecipients = useMemo(
    () => [...new Set(custodyEligible(packages, { date, recipient: '' }).map(item => item.recipient).filter(Boolean))].sort(),
    [packages, date])
  const eligible = useMemo(
    () => type === 'inbound'
      ? inboundEligible(packages, { date, location: manifestLocation })
      : custodyEligible(packages, { date, recipient: custodyRecipient }),
    [type, packages, date, manifestLocation, custodyRecipient])
  const groups = useMemo(
    () => type === 'custody' ? groupByRecipient(eligible) : [{ recipient: '', items: eligible }],
    [type, eligible])
  const [chosen, setChosen] = useState([])
  useEffect(() => setChosen(eligible), [type, date, manifestLocation, custodyRecipient, packages.length])
  const toggle = item => setChosen(current => current.some(value => value.trackingNumber === item.trackingNumber)
    ? current.filter(value => value.trackingNumber !== item.trackingNumber)
    : [...current, item])
  const included = item => chosen.some(value => value.trackingNumber === item.trackingNumber)
  const proposeId = () => {
    const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
    const suffix = crypto.randomUUID().replaceAll('-', '').slice(0, 6).toUpperCase()
    return `MNF-${stamp}-${suffix}`
  }
  const [manifestId, setManifestId] = useState(proposeId)
  useEffect(() => setManifestId(proposeId()), [manifests.length])
  const finalize = () => onFinalize({
    type,
    date,
    location: type === 'inbound' ? manifestLocation : '',
    recipient: type === 'custody' ? custodyRecipient : '',
    manifestId,
    trackingNumbers: chosen.map(item => item.trackingNumber).join('|')
  })
  return <><PageHeader title="Manifests" instruction="Prepare audited package lists and review finalized manifests." />
    <Card><Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 2 }}><Tab label="Prepare manifest" /><Tab label={`Manifest register (${manifests.length})`} /></Tabs><Divider />
      {tab === 0 ? <CardContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <FormControl sx={{ minWidth: 220 }}><InputLabel>Purpose</InputLabel><Select label="Purpose" value={type} onChange={event => setType(event.target.value)}><MenuItem value="inbound">Inbound receiving</MenuItem><MenuItem value="custody">Recipient custody</MenuItem></Select></FormControl>
          <TextField type="date" label="Received date" value={date} onChange={event => setDate(event.target.value)} InputLabelProps={{ shrink: true }} sx={{ minWidth: 200 }} />
          {type === 'inbound' && <FormControl sx={{ minWidth: 220 }}><InputLabel>Location</InputLabel><Select label="Location" value={manifestLocation} onChange={event => setManifestLocation(event.target.value)}><MenuItem value="">All locations</MenuItem>{locations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}
          {type === 'custody' && <FormControl sx={{ minWidth: 240 }}><InputLabel>Recipient</InputLabel><Select label="Recipient" value={custodyRecipient} onChange={event => setCustodyRecipient(event.target.value)}><MenuItem value="">All recipients</MenuItem>{custodyRecipients.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}
        </Stack>
        <Typography color="text.secondary" sx={{ my: 2 }}>Review exact membership before finalization. Finalization writes immutable audit events even if printing is canceled.</Typography>
        <TableContainer sx={{ maxHeight: 360 }}><Table size="small" stickyHeader><TableHead><TableRow><TableCell padding="checkbox"></TableCell><TableCell>Tracking</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell><TableCell>Recipient</TableCell></TableRow></TableHead>
          <TableBody>{groups.map(group => <React.Fragment key={group.recipient || 'all'}>
            {type === 'custody' && <TableRow><TableCell colSpan={5} sx={{ bgcolor: 'action.hover', fontWeight: 700 }}>{group.recipient} · {group.items.length}</TableCell></TableRow>}
            {group.items.map(item => <TableRow key={item.trackingNumber}><TableCell padding="checkbox"><Checkbox checked={included(item)} onChange={() => toggle(item)} /></TableCell><TableCell className="ct-mono">{item.trackingNumber}</TableCell><TableCell>{item.carrier}</TableCell><TableCell>{item.location}</TableCell><TableCell>{item.recipient || 'Unassigned'}</TableCell></TableRow>)}
          </React.Fragment>)}</TableBody></Table></TableContainer>
        <Typography sx={{ my: 1 }}>{chosen.length} included · {Math.max(0, eligible.length - chosen.length)} excluded</Typography>
        <Typography variant="body2">Proposed manifest ID: <b>{manifestId}</b> · Prepared: {formatDate(new Date().toISOString())}</Typography>
        {chosen.length > 100 && <Alert severity="warning" sx={{ my: 2 }}>Audited manifests are limited to 100 packages. Split this selection.</Alert>}
        <Button variant="contained" startIcon={<PrintRounded />} disabled={!chosen.length || chosen.length > 100} onClick={finalize} sx={{ mt: 1 }}>Finalize and open print view</Button>
      </CardContent>
      : manifests.length ? <TableContainer><Table><TableHead><TableRow><TableCell>Manifest ID</TableCell><TableCell>Type</TableCell><TableCell>Location/recipient</TableCell><TableCell>Prepared</TableCell><TableCell>Packages</TableCell><TableCell>Checksum</TableCell><TableCell></TableCell></TableRow></TableHead><TableBody>{manifests.map(item => <TableRow key={item.manifestId}><TableCell className="ct-mono" sx={{ fontWeight: 700 }}>{item.manifestId}</TableCell><TableCell>{item.type}</TableCell><TableCell>{item.location}</TableCell><TableCell>{formatDate(item.preparedUtc)}</TableCell><TableCell>{item.count}</TableCell><TableCell sx={{ maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }} title={item.checksum}>{item.checksum || 'Pending'}</TableCell><TableCell><Button size="small" onClick={() => onReprint(item.manifestId)}>Reprint</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer> : <EmptyState />}
    </Card></>
}
```

Notes:
- `React` is already imported (line 1), so `React.Fragment` is available; `useMemo` is already imported (line 1).
- The component no longer reads `session`; the render call at ~line 307 still passes `session={session}` harmlessly. Optionally simplify that call to drop `session={session}` — not required.
- Custody rows are rendered grouped with a subheader row per recipient (Unassigned last, from `groupByRecipient`). Inbound renders a single ungrouped bucket.
- Finalize sends `date` plus the active `location`/`recipient` filter so the backend auto-select path (Task 2) can re-derive eligibility even when the explicit `trackingNumbers` list is also supplied.

- [ ] **Step 4: Build the frontend**

Run (from `commercial-tracking-java/frontend/`): `npm run build`
Expected: Vite build succeeds (no unresolved imports, no JSX errors) and emits the bundle under `frontend/dist`.

Then run `npm test` again to confirm the whole chain (scanner + eligibility) is green.

- [ ] **Step 5: Manual UI check**

Run `npm run dev` (or the full app build) and open the Manifests page: confirm the date picker defaults to today, switching Purpose to Recipient custody shows recipient subheaders with Unassigned last, and changing the date re-derives the eligible list. Finalizing a small custody selection produces a manifest and opens the DOCX print view.

- [ ] **Step 6: Commit**

```bash
git add commercial-tracking-java/frontend/src/main.jsx
git commit -m "feat(ui): manifest date picker and recipient-grouped custody preparation"
```

---

## Self-Review

**Spec §3 (manifest QR + DOCX + HTML) coverage:**
- Manifest-level QR (manifest ID) in header, per-package QR (tracking number) on ≤20 detailed layout; HTML inline SVG, DOCX embedded PNG (~1 inch): Task 1 (`qrSvg`, `headerPng`, per-row `Cell.image`). ✓
- >20 (up to 100): compact listing, header QR only, per-package omitted + on-document note: Task 1 (`detailed` flag, `high-density` table, omission paragraph/`<p><i>`). ✓
- Header shows scoped date + location (inbound) / recipient scope (custody), host zone + shared `timeFormat`, no-seconds Prepared line, minute-precision UTC line: Task 1 via `TimeFormat.prepared`/`.utcMinute`, `Date:` field, `class="prepared"` span. ✓
- Custody recipient sections (alphabetical, Unassigned last); numbering + density from total count; HTML section headings, DOCX heading + table per section: Task 1 (`sections`, continuous `number`/`dnum`). ✓
- DOCX replaces PDF; checksum over DOCX; `Result{path=.docx, htmlPath, checksum}`: Task 1. ✓
- `write(...)` drops `timeZone`, adds `date` + `timeFormat`; all overloads + the sole non-test caller updated: Task 1 (overloads) + Task 2 (`BrowserServer.manifest()`). ✓
- `ManifestWriterTest` extended: header SVG + per-package SVG (≤20), DOCX `word/media/image*.png` counts (header+per-package ≤20, header-only >20), custody sections incl. Unassigned, scoped date, 12h/24h no-seconds Prepared line, checksum over `.docx`, `noDesktop` kept, layout-class assertions updated: Task 1 Step 1. ✓

**Spec §4 (void / custody / date-scoping eligibility) coverage:**
- Void excluded from auto-select and hard-rejected when explicitly supplied: Task 2 (`continue` on VOIDED; `throw` on explicit VOIDED). ✓
- Eligibility from all packages by host-zone received date, minus per-type already-manifested; inbound single-location kept; custody status ∈ {READY_FOR_PICKUP, PICKED_UP}, optional recipient, multi-recipient, old same-recipient rule removed: Task 2. ✓
- `date` request param default today host-zone + validated; host zone + shared `timeFormat` + date passed to `ManifestWriter`; `operationalTimeZone` removed: Task 2. ✓
- `manifestMaps` label includes date + scope (inbound location / custody recipient): Task 2 (`addressee = "<date> · <scope>"`, `manifestMaps` fallback). ✓
- Explicit checkbox path still validated (void reject + per-type guard): Task 2. ✓
- Frontend pure `manifestEligibility.js` (`inboundEligible`/`custodyEligible`/`groupByRecipient`) excluding VOIDED with date+status+location/recipient rules, Node-tested and wired into `npm test`; `ManifestWorkspace` date picker (local-date default), recipient grouping, `date` on finalize: Tasks 3–4. ✓

**Placeholder scan:** no "TBD"/"similar to"/"add QR here"; every code block is complete and compilable. The one `System.out.println` is an explicit, removable manual-check aid (Task 1 Step 5). ✓

**Name/signature consistency with the shared contract:** `QrCode.encode(String).toPng(int scale, int quietModules)` and `qr.size`/`qr.modules[row][col]`; `DocxWriter.heading/paragraph/table/save` + `Cell.text/Cell.image(byte[],int,int)`; `TimeFormat.prepared(String, ZoneId, String)` / `.utcMinute(String)`; `PackageState.receivedUtc`; `ManifestWriter.write(root, manifestId, type, scope, date, timeFormat, events)` used identically in `ManifestWriter`, `ManifestWriterTest`, and `BrowserServer.manifest()`. ✓

**Risks / assumptions:**
- Depends on Plan 1 (`QrCode`, `DocxWriter`) and Plan 4 (`TimeFormat`, `PackageState.receivedUtc`, `timeFormat` setting, `operationalTimeZone` removed) being committed first; otherwise compilation fails — block, do not stub.
- The 12h/24h Prepared-line assertions assume `TimeFormat` renders AM/PM for `"12h"`, no meridiem for `"24h"`, and never seconds — the Plan 4 contract. If `TimeFormat` output differs, adjust only the `timeFormatting` assertions, not the writer.
- A recipient literally named `"Unassigned"` would merge into the unassigned bucket in both `ManifestWriter.sections` and `groupByRecipient` — accepted edge case (also present in the spec's grouping model).
- No automated `BrowserServer` test exists; Task 2 relies on compile + `ManifestWriterTest` + the manual smoke checks.
```