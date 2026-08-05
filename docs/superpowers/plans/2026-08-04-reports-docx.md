# Reports PDF→DOCX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement spec §5 — replace the report's hand-rolled PDF output with a `.docx` produced via the shared `DocxWriter`, keeping the CSV and HTML outputs exactly as they are; switch `BrowserServer.report()` and the frontend `ReportsWorkspace` to the new `docxFile` response field and host time zone; and, as the final step, retire `PortablePdf`/`PortablePdfTest` once no code references them.

**Architecture:** `ReportWriter` still writes three files per run — CSV, HTML, and (now) DOCX instead of PDF. The DOCX mirrors the current `pdfLines` content: a heading, four meta paragraphs, and a single table (header = selected column labels, body = one row per record) built from the existing `label(...)`/`cell(...)` helpers. `ReportWriter.Result.pdf` becomes `Result.docx` (a `Path`). `BrowserServer.report()` returns `docxFile` instead of `pdfFile` and defaults `timeZone` to the host zone. The React `ReportsWorkspace` relabels the primary button to "Create DOCX", computes the zone from the browser, and stops consuming the removed `operationalTimeZone` shared setting. Report **timestamp columns remain full UTC** — this plan does not reformat exported timestamps.

**Tech Stack:** Java 8 (`javac --release 8`), pure JDK, no third-party jars. Java tests are plain `main()` classes run by `build.ps1`. Frontend is React built with Vite (`npm run build`); no report-specific Node unit test is added (this workspace's logic is UI-bound and verified via build + manual check).

## Global Constraints

- Pure JDK only — no third-party libraries. Build is `javac --release 8 -encoding UTF-8` (driven by `build.ps1`).
- Java test classes are `public final class XxxTest { public static void main(String[] args) throws Exception { ... System.out.println("XxxTest: PASS"); } }` and are wired into `build.ps1`. `ReportWriterTest` is **already** wired (do not re-add its run line).
- Package is `org.commercialtracking`; source under `commercial-tracking-java/src/main/java/org/commercialtracking/`, tests under `commercial-tracking-java/src/test/java/org/commercialtracking/`.
- Java build/test command (run from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`.
- Frontend build/test commands (run from `commercial-tracking-java/frontend/`): `npm run build` and `npm test`.
- All paths below are relative to the repo root `F:\PowerApps`.
- **Consumes** the shared `DocxWriter` exactly as named by the Document Core plan: `heading(String)`, `paragraph(String)`, `table(java.util.List<java.util.List<DocxWriter.Cell>> rows)`, `save(Path)`, `DocxWriter.Cell.text(String)`, `DocxWriter.Cell.image(byte[],int,int)`. Do not redefine or alter `DocxWriter`.
- **Report timestamp columns remain full UTC** — the `time` column (`Occurred UTC`) and any instant strings are emitted verbatim; no seconds-dropping, no host-zone reformatting in reports (that is a manifest/settings concern, not this plan).
- No QR codes in reports.
- **Dependency:** Tasks 1–3 depend on the Document Core plan (which produces `DocxWriter`) being merged. Task 4 additionally depends on the Manifests plan (which removes `ManifestWriter`'s `PortablePdf` usage) being merged — see Task 4's ordering caveat.

---

### Task 1: ReportWriter PDF→DOCX + ReportWriterTest

Replace the PortablePdf output with a `DocxWriter`-built `.docx`; keep CSV and HTML byte-for-byte identical in shape. Rename `Result.pdf` → `Result.docx`. Rename the `openPdf` flag to `openDoc` for clarity.

**Files:**
- Modify: `commercial-tracking-java/src/main/java/org/commercialtracking/ReportWriter.java`
- Modify: `commercial-tracking-java/src/test/java/org/commercialtracking/ReportWriterTest.java`

**Interfaces:**
- Consumes: `DocxWriter.heading/paragraph/table/save`, `DocxWriter.Cell.text` (from the Document Core plan); the existing private `label(String)`/`cell(Row,String)` helpers.
- Produces: `ReportWriter.Result` with public field `Path docx` (replacing `Path pdf`); `write(...)` still returns CSV + HTML + a document, now `.docx`.

- [ ] **Step 1: Write the failing test (edit ReportWriterTest first)**

In `commercial-tracking-java/src/test/java/org/commercialtracking/ReportWriterTest.java`, add a `ZipFile` import and replace the PDF assertion with a DOCX assertion. Keep `commercialtracking.noDesktop` set (line 12) and keep every other assertion unchanged.

Add this import after the existing `import java.util.List;` line:

```java
import java.util.zip.ZipFile;
```

Then replace this block:

```java
        check(html.contains("America/Los_Angeles"), "operational time zone");
        check(new String(Files.readAllBytes(receiving.pdf), 0, 8, StandardCharsets.ISO_8859_1)
                .startsWith("%PDF-1.4"), "report PDF");
```

with:

```java
        check(html.contains("America/Los_Angeles"), "operational time zone");
        check(Files.exists(receiving.docx), "report DOCX exists");
        try (ZipFile zip = new ZipFile(receiving.docx.toFile())) {
            check(zip.getEntry("[Content_Types].xml") != null, "docx content-types part");
            check(zip.getEntry("word/document.xml") != null, "docx document part");
        }
```

(The existing CSV assertions — `' +IN-RANGE` present, `=TRACKING` excluded — and HTML assertions — the extract label and `America/Los_Angeles` — stay untouched, proving CSV + HTML are unchanged in shape. The `outbound` count assertion also stays.)

- [ ] **Step 2: Run to verify it fails**

Run (from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`
Expected: compile error — `receiving.docx` does not exist yet (`Result` still has `pdf`).

- [ ] **Step 3: Rename the flag parameter to `openDoc` in ReportWriter**

In `commercial-tracking-java/src/main/java/org/commercialtracking/ReportWriter.java`, rename the `openPdf` parameter to `openDoc` in the two overloads that declare it.

Replace:

```java
    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection, boolean openPdf) throws IOException {
        return write(root, type, period, timeZone, from, to, events, projection, openPdf,
                "time|tracking|carrier|recipient|location|status|manifest|actor|device",
                "location", "occurred-asc", true);
    }

    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection, boolean openPdf, String columns,
                        String groupBy, String sortOrder, boolean includeSummary) throws IOException {
```

with:

```java
    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection, boolean openDoc) throws IOException {
        return write(root, type, period, timeZone, from, to, events, projection, openDoc,
                "time|tracking|carrier|recipient|location|status|manifest|actor|device",
                "location", "occurred-asc", true);
    }

    public Result write(Path root, String type, String period, String timeZone, Instant from, Instant to,
                        List<TrackingEvent> events, Projection projection, boolean openDoc, String columns,
                        String groupBy, String sortOrder, boolean includeSummary) throws IOException {
```

(The two-arg and no-flag overloads at the top of the class pass the boolean literal `true` and forward positionally, so they need no edit.)

- [ ] **Step 4: Replace the PDF build/open with a DOCX build/open**

In the same file, replace this block (the `.pdf` path, the `pdfLines` accumulation, the `PortablePdf.write` call, the `Desktop.browse` on the PDF, and the `Result` construction):

```java
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
```

with:

```java
        Path docx = dir.resolve("Commercial-Tracking-" + stamp + ".docx");
        Files.write(csv, csv(rows, selectedColumns).getBytes(StandardCharsets.UTF_8));
        Files.write(html, html(type, timeZone, from, to, rows, selectedColumns, includeSummary).getBytes(StandardCharsets.UTF_8));
        DocxWriter document = new DocxWriter();
        document.heading("Reporting Extract - not an audited manifest");
        document.paragraph("Operational time zone: " + timeZone);
        document.paragraph("Inclusive start UTC: " + from);
        document.paragraph("Exclusive end UTC: " + to);
        document.paragraph("Rows: " + rows.size());
        List<List<DocxWriter.Cell>> tableRows = new ArrayList<List<DocxWriter.Cell>>();
        List<DocxWriter.Cell> header = new ArrayList<DocxWriter.Cell>();
        for (String column : selectedColumns) header.add(DocxWriter.Cell.text(label(column)));
        tableRows.add(header);
        for (Row row : rows) {
            List<DocxWriter.Cell> cells = new ArrayList<DocxWriter.Cell>();
            for (String column : selectedColumns) cells.add(DocxWriter.Cell.text(cell(row, column)));
            tableRows.add(cells);
        }
        document.table(tableRows);
        document.save(docx);
        if (openDoc && !Boolean.getBoolean("commercialtracking.noDesktop") && Desktop.isDesktopSupported())
            Desktop.getDesktop().browse(docx.toUri());
        return new Result(docx, html, csv, rows.size());
```

Notes: `DocxWriter` is in the same package `org.commercialtracking`, so no new import is needed. `java.util.List` and `java.util.ArrayList` are already imported. `label(column)` is non-null for every entry of `selectedColumns` (it is filtered by `label(column) != null` earlier), and `cell(...)` never returns null; `DocxWriter.Cell.text`/`DocxWriter.xml` tolerate empty strings regardless. The timestamp/`time` column is emitted verbatim via `cell(row, "time")` → `row.event.occurredUtc` (full UTC — unchanged).

- [ ] **Step 5: Update the Result type (pdf → docx)**

In the same file, replace the `Result` class:

```java
    public static final class Result {
        public final Path pdf;
        public final Path html;
        public final Path csv;
        public final int count;
        Result(Path pdf, Path html, Path csv, int count) { this.pdf = pdf; this.html = html; this.csv = csv; this.count = count; }
    }
```

with:

```java
    public static final class Result {
        public final Path docx;
        public final Path html;
        public final Path csv;
        public final int count;
        Result(Path docx, Path html, Path csv, int count) { this.docx = docx; this.html = html; this.csv = csv; this.count = count; }
    }
```

- [ ] **Step 6: Run to verify it passes**

Run (from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`
Expected: build compiles and output includes `ReportWriterTest: PASS`.

(At this point `BrowserServer.report()` still references `output.pdf` and will fail to compile. If `build.ps1` compiles `BrowserServer` before running `ReportWriterTest`, the compile error will surface here — that is expected; proceed to Task 2 in the same working tree and commit both together, OR make Task 2's edits before running the full build. To run just this test in isolation before Task 2, compile and run `ReportWriterTest` against a classpath that excludes `BrowserServer`; the simplest path is to complete Task 2 next and build once.)

- [ ] **Step 7: Commit**

```bash
git add commercial-tracking-java/src/main/java/org/commercialtracking/ReportWriter.java \
        commercial-tracking-java/src/test/java/org/commercialtracking/ReportWriterTest.java
git commit -m "feat(reports): replace PortablePdf output with DocxWriter DOCX"
```

---

### Task 2: BrowserServer.report() — docxFile response + host zone default

Change the report response to advertise `docxFile` and default the request `timeZone` to the host machine zone. Only `report()` is edited.

**Files:**
- Modify: `commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java` (only the `report()` method, ~lines 580–604)

**Interfaces:**
- Consumes: `ReportWriter.Result.docx` (from Task 1).
- Produces: `/api/report` response with `docxFile` instead of `pdfFile`; unchanged `htmlFile`, `csvFile`, `count`, `savedCopy`.

- [ ] **Step 1: Change the timeZone default to the host zone**

In `report()`, replace:

```java
        String timeZone = value(request, "timeZone", "UTC");
```

with:

```java
        String timeZone = value(request, "timeZone", java.time.ZoneId.systemDefault().getId());
```

(`ZoneId` is already imported in this file — `reportBounds` calls `ZoneId.of(...)` — so `ZoneId.systemDefault()` is available unqualified; the fully-qualified form above is used to be unambiguous and edit-safe. Either compiles.)

- [ ] **Step 2: Change the response field pdfFile → docxFile**

In `report()`, replace:

```java
        response.put("pdfFile", output.pdf.getFileName().toString());
```

with:

```java
        response.put("docxFile", output.docx.getFileName().toString());
```

Do NOT touch `htmlFile`, `csvFile`, `count`, or `savedCopy`. Do NOT change `reportBounds(...)` (its own `"UTC"` default is out of scope; the frontend now always sends an explicit `timeZone`, and `manifest()`/`saveSharedSettings`/package maps are owned by other plans).

- [ ] **Step 3: Run to verify it passes**

Run (from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`
Expected: full build compiles; output includes `ReportWriterTest: PASS` and the build reaches its normal completion (jar/manifest steps).

- [ ] **Step 4: Commit**

```bash
git add commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java
git commit -m "feat(reports): report() returns docxFile and defaults to host time zone"
```

---

### Task 3: Frontend ReportsWorkspace — Create DOCX + host zone

Relabel the primary button to "Create DOCX", drop the removed `operationalZone`/`operationalTimeZone` prop, and derive the zone from the browser.

**Files:**
- Modify: `commercial-tracking-java/frontend/src/main.jsx` (only `ReportsWorkspace` (~line 536) and its render site (~line 308))

**Interfaces:**
- Consumes: `api.report`/`api.reportRange` (unchanged signatures in `frontend/src/api.js`); `DescriptionRounded` icon (already imported at the top of `main.jsx`).
- Produces: report requests carrying `timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone`; a "Create DOCX" button.

- [ ] **Step 1: Drop the operationalZone prop at the render site**

At the reports render site (~line 308), replace:

```jsx
      {page === 'reports' && <ReportsWorkspace operationalZone={state.sharedSettings?.operationalTimeZone} events={activity} packages={packages} onCreate={async payload => { try { const response = await api.report(payload); setToast(`${response.count} rows exported to ${response.csvFile}`) } catch (error) { setToast(error.message) } }} />}
```

with:

```jsx
      {page === 'reports' && <ReportsWorkspace events={activity} packages={packages} onCreate={async payload => { try { const response = await api.report(payload); setToast(`${response.count} rows exported to ${response.csvFile}`) } catch (error) { setToast(error.message) } }} />}
```

(The toast intentionally still reads `response.csvFile`: the CSV is always produced by every action, so this message stays accurate. There is no `pdfFile` reference in the toast to change — the backend's renamed `docxFile` field is simply not referenced by the toast.)

- [ ] **Step 2: Drop operationalZone from the component signature**

Replace:

```jsx
function ReportsWorkspace({ onCreate, operationalZone, events, packages }) {
```

with:

```jsx
function ReportsWorkspace({ onCreate, events, packages }) {
```

- [ ] **Step 3: Compute the zone from the browser**

Replace:

```jsx
  const zone = operationalZone || Intl.DateTimeFormat().resolvedOptions().timeZone
```

with:

```jsx
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone
```

(This `zone` already flows into `api.reportRange` in the effect and into the `options` object passed to `api.report` — both then send it as `timeZone`, so no other change to those call sites is needed.)

- [ ] **Step 4: Relabel the button to "Create DOCX"**

In the action-buttons block (~line 574), replace this single button:

```jsx
<Button variant="contained" startIcon={<PrintRounded />} onClick={() => onCreate({ ...options, action: 'print', saveCopy: 'false' })}>Create PDF / Print</Button>
```

with:

```jsx
<Button variant="contained" startIcon={<DescriptionRounded />} onClick={() => onCreate({ ...options, action: 'print', saveCopy: 'false' })}>Create DOCX</Button>
```

(`DescriptionRounded` is already imported at the top of `main.jsx`. `PrintRounded` remains imported and used elsewhere (the manifest reprint button ~line 531), so its import is not orphaned. Leave the "Export CSV" and "Save copy to shared reports" buttons and the on-screen "Operational time zone: {zone}" label unchanged — the HTML report and its own print button are untouched by this plan.)

- [ ] **Step 5: Build the frontend to verify**

Run (from `commercial-tracking-java/frontend/`): `npm run build`
Expected: Vite build succeeds with no errors and emits a new `dist/assets/index-*.js`.

Then run the existing frontend tests to confirm nothing regressed: `npm test`
Expected: all existing Node tests pass (this plan adds none).

- [ ] **Step 6: Manual UI check**

Start the app, open the Reports page, and confirm: the primary button reads "Create DOCX"; the "Operational time zone:" line shows the host machine's IANA zone (e.g. `America/Los_Angeles`), not `UTC`; clicking "Create DOCX" produces and opens a `.docx` that renders in Word with the heading, meta lines, and the row table; "Export CSV" still produces a CSV; the toast reports the row count and CSV file name.

- [ ] **Step 7: Commit**

```bash
git add commercial-tracking-java/frontend/src/main.jsx
git commit -m "feat(reports): ReportsWorkspace Create DOCX button and host time zone"
```

---

### Task 4: Retire PortablePdf (FINAL — has a cross-plan ordering caveat)

Delete `PortablePdf.java`, `PortablePdfTest.java`, and the `PortablePdfTest` run block in `build.ps1`. This must be the last task.

**Ordering caveat (must be satisfied before executing this task):** `PortablePdf` is referenced by TWO producers — `ReportWriter` (removed in Task 1 of this plan) and `ManifestWriter` (`ManifestWriter.java:116`, `PortablePdf.write(output, ...)`), which is removed by the **Manifests plan** (spec §3, Plan 2). At the start of this repo state, `ManifestWriter` STILL calls `PortablePdf`. Therefore this task can only run after the Manifests plan has been merged. The grep in Step 1 is the gate: do not delete anything until it shows zero non-test references.

**Files:**
- Delete: `commercial-tracking-java/src/main/java/org/commercialtracking/PortablePdf.java`
- Delete: `commercial-tracking-java/src/test/java/org/commercialtracking/PortablePdfTest.java`
- Modify: `commercial-tracking-java/build.ps1` (remove the `PortablePdfTest` run block)

**Interfaces:**
- Consumes: confirmation (via grep) that no production or test code references `PortablePdf`.
- Produces: a build with no PDF codepath.

- [ ] **Step 1: Confirm no remaining references (the gate)**

Run (from `commercial-tracking-java/`): `grep -rn PortablePdf src/`
Expected AFTER the Manifests plan is merged: the only hits are `PortablePdf.java` itself (the class definition) and `PortablePdfTest.java` (the test). There must be NO reference in `ReportWriter.java` (removed in Task 1) and NO reference in `ManifestWriter.java`.

STOP if `ManifestWriter.java` still contains `PortablePdf.write(...)`: that means the Manifests plan (Plan 2) has not been merged. Do not proceed — coordinate to merge that plan first, then re-run this grep. Deleting `PortablePdf` while `ManifestWriter` still calls it would break the build.

- [ ] **Step 2: Delete the two Java files**

```bash
git rm commercial-tracking-java/src/main/java/org/commercialtracking/PortablePdf.java \
       commercial-tracking-java/src/test/java/org/commercialtracking/PortablePdfTest.java
```

- [ ] **Step 3: Remove the PortablePdfTest run block from build.ps1**

In `commercial-tracking-java/build.ps1`, delete these two lines (the `PortablePdfTest` invocation and its error guard):

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.PortablePdfTest
    if ($LASTEXITCODE -ne 0) { throw "Portable PDF tests failed." }
```

(Leave the surrounding `AppConfigTest` block above and `PerformanceSmokeTest` block below intact.)

- [ ] **Step 4: Confirm the deletion is complete**

Run (from `commercial-tracking-java/`): `grep -rn PortablePdf src/ build.ps1`
Expected: no output (exit code 1 from grep — zero matches).

- [ ] **Step 5: Run the full build to verify nothing regressed**

Run (from `commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`
Expected: build compiles; the test sequence runs through `ReportWriterTest: PASS` (and the other suites) with no `PortablePdfTest` line and no failures.

- [ ] **Step 6: Commit**

```bash
git add commercial-tracking-java/build.ps1
git commit -m "chore(reports): retire PortablePdf now that reports and manifests emit DOCX"
```

(The `git rm` from Step 2 is already staged; this commit captures the deletions plus the `build.ps1` edit.)

---

## Self-Review

**Spec §5 coverage (Reports — PDF→DOCX):**
- `ReportWriter` keeps CSV + HTML, replaces PDF with a `DocxWriter` DOCX; `Result.pdf` → `Result.docx` → Task 1. ✓
- DOCX content mirrors the old `pdfLines`: heading + four meta paragraphs + one table (header labels, body rows) via `label(...)`/`cell(...)` → Task 1 Step 4. ✓
- `PortablePdf.write` + `Desktop.browse(pdf)` replaced by `document.save(docx)` + `Desktop.browse(docx)`, respecting `openDoc` and `commercialtracking.noDesktop` → Task 1 Step 4. ✓
- `BrowserServer.report()` returns `docxFile` (from `output.docx`) instead of `pdfFile`; `timeZone` default `"UTC"` → `ZoneId.systemDefault().getId()`; other fields unchanged; `report()` only → Task 2. ✓
- Frontend `ReportsWorkspace`: button "Create PDF / Print" → "Create DOCX"; `operationalZone` prop dropped at render site + signature; zone from `Intl.DateTimeFormat().resolvedOptions().timeZone`; sent as `timeZone` in `api.report`/`api.reportRange` → Task 3. ✓
- Report timestamp columns remain full UTC (the `time`/`Occurred UTC` column emitted verbatim; no reformatting) → Task 1 Step 4 note + Global Constraints. ✓
- `ReportWriterTest` extended: `Result.docx` exists and is a valid ZIP containing `word/document.xml` and `[Content_Types].xml`; CSV + HTML assertions retained (CSV row inclusion/exclusion, HTML extract label + zone); `commercialtracking.noDesktop` kept set → Task 1 Step 1. ✓

**PortablePdf fully removed:** Task 4 deletes `PortablePdf.java`, `PortablePdfTest.java`, and the `build.ps1` run block, gated by a grep that confirms zero references — including the explicit ManifestWriter cross-plan ordering caveat (Plan 2 must merge first, since `ManifestWriter.java:116` still calls `PortablePdf` today). ✓

**Placeholder scan:** No "TBD"/"handle errors"/"similar to". Every edit shows the exact before/after text with complete code. ✓

**Name consistency:** `Result.docx`, `output.docx`, `docxFile`, `document.save(docx)`, `openDoc`, `DocxWriter.Cell.text`, and the frontend `Create DOCX`/`timeZone`/`zone` names are used identically across `ReportWriter`, `ReportWriterTest`, `BrowserServer.report()`, and `ReportsWorkspace`. The `openPdf`→`openDoc` rename is applied in both declaring overloads; the literal-`true` forwarders are unaffected. `DescriptionRounded` is confirmed already imported; `PrintRounded` remains used elsewhere so its import is not orphaned. ✓

**Ownership boundaries:** Only `report()` in `BrowserServer` is edited (not `manifest()`/`reportBounds()`/`saveSharedSettings`/package maps); only `ReportsWorkspace` and its render site in `main.jsx` (not `ManifestWorkspace`/`SettingsWorkspace`); `api.js` is consumed unchanged. ✓
