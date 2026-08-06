# Medical Supply Tracking — Plan 4: Presentation Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the user-facing layer over the Plan 3 API: a management-report PDF, a Swing `--classic-ui` fallback, the React/MUI browser SPA (replacing Plan 3's minimal bundled UI) with QR label printing, and release/qualification packaging.

**Architecture:** All presentation consumes the tested Plan 3 surfaces (`AppService`, `BrowserServer`'s `/api/*`, `ManagementReport`). `PortablePdf` (ported from `apps/commercial-tracking-java`, dependency-free) adds a PDF to the management report. `SwingApp` drives `AppService` directly for the offline desktop fallback. The React/MUI SPA is built by Vite into `src/main/resources/web` (replacing the hand-written UI), talking only to `/api/*`; QR codes render client-side from a bundled library (offline). `build.ps1` gains an npm frontend build; packaging adds README/TESTING/RELEASE_NOTES and `dist-review/qualification` evidence.

**Tech Stack:** Java 8 (`javac --release 8`, Swing, `com.sun.net.httpserver`), and — new in this plan — Node/npm with Vite + React 19 + MUI 7 + a QR library, at build time only. The shipped JAR remains Java-8-only with the SPA embedded.

## Global Constraints

- Java sources target Java 8 bytecode (`javac --release 8`), Java SE + `com.sun.net.httpserver` only, no third-party Java libraries.
- **Node/npm are now in scope** but build-time only: the SPA compiles to static assets embedded in the JAR. The running application needs only Java 8 + a browser (or Swing).
- Package root: `org.medsupply`. **Formatting: conventional, readable, multi-line Java** — do not minify.
- Depends on Plans 1–3 (built, green on `main`): `AppService` (`configure`, `reload`, `stock`, `catalog`, `dashboard(Instant)`, `reorder(Instant)`, `snapshot(Instant)`, `scan`, `receive`, `pick`, `adjust`, `archive`, `registerProduct`, `lookupGudid`, `identity`, `store`, `BadRequest`), `BrowserServer` (`start`, `startAndOpen`, `stop`, `token`, `/api/*`), `ManagementReport` (`write`, `renderHtml`, `renderReorderCsv`, `Result{html,csv}`), `AppConfig`, `Json`.
- Java tests are framework-free `*Test` classes printing `XxxTest: PASS`; `build.ps1` auto-discovers them.
- The Vite build writes to `apps/medical-supply-java/src/main/resources/web` with `emptyOutDir: true`, so it **replaces** Plan 3's committed `web/index.html` + `web/app.js` (removed in Task 3). The built `web/` becomes generated output (git-ignored); source lives under `frontend/`.

---

### Task 1: Management-report PDF (port PortablePdf)

**Files:**
- Create: `apps/medical-supply-java/src/main/java/org/medsupply/PortablePdf.java`
- Modify: `apps/medical-supply-java/src/main/java/org/medsupply/ManagementReport.java`
- Modify: `apps/medical-supply-java/src/main/java/org/medsupply/BrowserServer.java` (report response includes `pdfFile`)
- Test: `apps/medical-supply-java/src/test/java/org/medsupply/PortablePdfTest.java`
- Modify: `apps/medical-supply-java/src/test/java/org/medsupply/ManagementReportTest.java`

**Interfaces:**
- Produces:
  - `PortablePdf.write(java.nio.file.Path output, String title, java.util.List<String> lines)` — writes a valid single-font PDF (paginates at 48 lines).
  - `ManagementReport.Result` gains `public final java.nio.file.Path pdf;`; `ManagementReport.write(...)` now also emits `management-report-<utc>.pdf`; new package-visible `java.util.List<String> renderPdfLines(DashboardMetrics, java.util.List<ReorderSuggestion>, java.util.List<StockLine>, java.time.Instant)`.

- [ ] **Step 1: Write the failing test for `PortablePdf`**

`apps/medical-supply-java/src/test/java/org/medsupply/PortablePdfTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class PortablePdfTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("medsupply-pdf");
        Path out = dir.resolve("r.pdf");
        PortablePdf.write(out, "Report", Arrays.asList("Line one", "Line (two) with )chars\\", "Ünïcödé"));
        byte[] bytes = Files.readAllBytes(out);
        String head = new String(Arrays.copyOfRange(bytes, 0, 8), "ISO-8859-1");
        check(head.startsWith("%PDF-1."), "pdf header: " + head);
        String body = new String(bytes, "ISO-8859-1");
        check(body.contains("%%EOF"), "pdf trailer");
        check(body.contains("/Type /Catalog"), "catalog object");
        System.out.println("PortablePdfTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File apps/medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... PortablePdf`.

- [ ] **Step 3: Add `PortablePdf` (ported, dependency-free)**

`apps/medical-supply-java/src/main/java/org/medsupply/PortablePdf.java`:

```java
package org.medsupply;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PortablePdf {
    private PortablePdf() {}

    public static void write(Path output, String title, List<String> lines) throws IOException {
        List<List<String>> pages = new ArrayList<List<String>>();
        List<String> page = new ArrayList<String>();
        for (String line : lines) {
            if (page.size() == 48) { pages.add(page); page = new ArrayList<String>(); }
            page.add(line);
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(page);

        int pageCount = pages.size();
        int objectCount = 3 + pageCount * 2;
        String[] objects = new String[objectCount + 1];
        objects[1] = "<< /Type /Catalog /Pages 2 0 R >>";
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) kids.append(4 + i * 2).append(" 0 R ");
        objects[2] = "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>";
        objects[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>";
        for (int i = 0; i < pageCount; i++) {
            int pageObject = 4 + i * 2;
            int contentObject = pageObject + 1;
            String content = content(title, pages.get(i), i + 1, pageCount);
            objects[pageObject] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObject + " 0 R >>";
            objects[contentObject] = "<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length
                    + " >>\nstream\n" + content + "\nendstream";
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));
        long[] offsets = new long[objectCount + 1];
        for (int i = 1; i <= objectCount; i++) {
            offsets[i] = out.size();
            out.write((i + " 0 obj\n" + objects[i] + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        long xref = out.size();
        out.write(("xref\n0 " + (objectCount + 1) + "\n0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i <= objectCount; i++)
            out.write(String.format("%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.US_ASCII));
        out.write(("trailer\n<< /Size " + (objectCount + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(output, out.toByteArray());
    }

    private static String content(String title, List<String> lines, int page, int pages) {
        StringBuilder value = new StringBuilder("BT\n/F1 16 Tf\n50 755 Td\n(")
                .append(pdf(title)).append(") Tj\n/F1 9 Tf\n0 -22 Td\n");
        for (String line : lines)
            value.append("(").append(pdf(line)).append(") Tj\n0 -14 Td\n");
        value.append("ET\nBT\n/F1 8 Tf\n50 24 Td\n(Page ").append(page).append(" of ").append(pages)
                .append(") Tj\nET");
        return value.toString();
    }

    private static String pdf(String value) {
        if (value == null) return "";
        String ascii = value.replaceAll("[^\\x20-\\x7E]", "?");
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
```

- [ ] **Step 4: Run to verify `PortablePdfTest` passes**

Run: `powershell -File apps/medical-supply-java/build.ps1`
Expected: `PortablePdfTest: PASS`.

- [ ] **Step 5: Add the PDF to `ManagementReport`**

In `ManagementReport.java`: (a) add `public final Path pdf;` to `Result` and update its constructor to `Result(Path html, Path csv, Path pdf)`; (b) in `write(...)`, after writing HTML/CSV, add:

```java
        Path pdf = reportsDir.resolve("management-report-" + stamp + ".pdf");
        PortablePdf.write(pdf, "Medical Supply Management Report", renderPdfLines(metrics, reorder, stock, now));
        return new Result(html, csv, pdf);
```

(remove the old `return new Result(html, csv);`), and add:

```java
    static java.util.List<String> renderPdfLines(DashboardMetrics m, java.util.List<ReorderSuggestion> reorder,
            java.util.List<StockLine> stock, java.time.Instant now) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add("Generated UTC: " + now.toString());
        lines.add("");
        lines.add("AT A GLANCE");
        lines.add("SKUs: " + m.distinctSkus + "   On-hand units: " + m.totalUnits
                + "   On-hand value: " + money(m.onHandValue));
        lines.add("Expired: " + m.expired + "   Expiring <=7d: " + m.expiring7
                + "   Expiring <=30d: " + m.expiring30 + "   Out of stock: " + m.outOfStock + "   Stale: " + m.stale);
        lines.add("");
        lines.add("REORDER");
        for (ReorderSuggestion s : reorder) {
            if (!s.needsReorder) continue;
            lines.add(s.gtin + "  " + s.name + "  on-hand " + s.onHand + "  order "
                    + s.suggestedOrderQty + "  est " + money(s.estimatedCost));
        }
        return lines;
    }
```

- [ ] **Step 6: Update `ManagementReportTest` for the PDF**

In `ManagementReportTest.java`, after the existing file-write assertion, add:

```java
        check(Files.isRegularFile(r.pdf), "pdf written");
        check(new String(Files.readAllBytes(r.pdf), "ISO-8859-1").startsWith("%PDF"), "pdf header");
```

- [ ] **Step 7: Expose `pdfFile` in the API**

In `BrowserServer.route`, in the `/api/report` branch, after `response.put("csvFile", ...)`, add:

```java
            response.put("pdfFile", result.pdf.getFileName().toString());
```

- [ ] **Step 8: Build, verify all green**

Run: `powershell -File apps/medical-supply-java/build.ps1`
Expected: `PortablePdfTest: PASS`, `ManagementReportTest: PASS`, and all others, then `Built: ...`.

- [ ] **Step 9: Commit**

```bash
git add apps/medical-supply-java/src/main/java/org/medsupply/PortablePdf.java apps/medical-supply-java/src/main/java/org/medsupply/ManagementReport.java apps/medical-supply-java/src/main/java/org/medsupply/BrowserServer.java apps/medical-supply-java/src/test/java/org/medsupply/PortablePdfTest.java apps/medical-supply-java/src/test/java/org/medsupply/ManagementReportTest.java
git commit -m "feat(medsupply): management report PDF"
```

---

### Task 2: Swing `--classic-ui` fallback

**Files:**
- Create: `apps/medical-supply-java/src/main/java/org/medsupply/SwingApp.java`
- Modify: `apps/medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java`
- Test: `apps/medical-supply-java/src/test/java/org/medsupply/SwingRowsTest.java`

**Interfaces:**
- Consumes: `AppService`, `AppConfig`, `GudidClient`, `HttpsFetcher`.
- Produces:
  - `SwingApp.launch(AppService service)` — builds and shows the desktop window (offline fallback: folder chooser, scan/receive field, inventory table, dashboard summary, export-report button).
  - `SwingApp.stockRow(StockLine line) -> Object[]` — package-visible pure formatter used by the table (unit-testable without a display).
  - `MedicalSupplyApp` routes `--classic-ui` to `SwingApp.launch(...)`.

- [ ] **Step 1: Write the failing test (pure formatter, no display)**

`apps/medical-supply-java/src/test/java/org/medsupply/SwingRowsTest.java`:

```java
package org.medsupply;

public final class SwingRowsTest {
    public static void main(String[] args) {
        StockLine line = new StockLine();
        line.name = "Stent";
        line.gtin = "00380740000010";
        line.lot = "L1";
        line.expirationIso = "2026-11-30";
        line.quantity = 7;
        Object[] row = SwingApp.stockRow(line);
        check(row.length == 5, "5 columns");
        check("Stent".equals(row[0]), "name");
        check("00380740000010".equals(row[1]), "gtin");
        check("L1".equals(row[2]), "lot");
        check("2026-11-30".equals(row[3]), "exp");
        check(Integer.valueOf(7).equals(row[4]), "qty");
        System.out.println("SwingRowsTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File apps/medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... SwingApp`.

- [ ] **Step 3: Write `SwingApp`**

`apps/medical-supply-java/src/main/java/org/medsupply/SwingApp.java`:

```java
package org.medsupply;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;

public final class SwingApp extends JFrame {
    private final AppService service;
    private final JTextField folder = new JTextField();
    private final JTextField scan = new JTextField();
    private final JTextField qty = new JTextField("1", 4);
    private final JLabel status = new JLabel("Select a synchronized folder to begin.");
    private final JLabel kpis = new JLabel(" ");
    private final DefaultTableModel model =
            new DefaultTableModel(new Object[] {"Name", "GTIN", "Lot", "Expiration", "Qty"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };

    private SwingApp(AppService service) {
        super("Medical Supply Tracking (classic)");
        this.service = service;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        build();
        if (service.configured()) refresh();
    }

    public static void launch(AppService service) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SwingApp(service).setVisible(true));
    }

    static Object[] stockRow(StockLine line) {
        return new Object[] {line.name, line.gtin, line.lot, line.expirationIso, Integer.valueOf(line.quantity)};
    }

    private void build() {
        JPanel top = new JPanel(new GridLayout(2, 1, 6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.add(new JLabel("Folder:"), BorderLayout.WEST);
        folderRow.add(folder, BorderLayout.CENTER);
        JButton choose = new JButton("Choose...");
        choose.addActionListener(e -> chooseFolder());
        JButton report = new JButton("Export report");
        report.addActionListener(e -> exportReport());
        JPanel folderButtons = new JPanel();
        folderButtons.add(choose);
        folderButtons.add(report);
        folderRow.add(folderButtons, BorderLayout.EAST);
        JPanel scanRow = new JPanel(new BorderLayout(6, 0));
        scanRow.add(new JLabel("Scan:"), BorderLayout.WEST);
        scanRow.add(scan, BorderLayout.CENTER);
        JPanel scanEast = new JPanel();
        scanEast.add(new JLabel("Qty"));
        scanEast.add(qty);
        JButton receive = new JButton("Receive");
        receive.addActionListener(e -> receive());
        scanEast.add(receive);
        scanRow.add(scanEast, BorderLayout.EAST);
        scan.addActionListener(e -> receive());
        top.add(folderRow);
        top.add(scanRow);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        bottom.add(kpis, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            service.configure(chooser.getSelectedFile().toPath());
            folder.setText(chooser.getSelectedFile().toString());
            refresh();
            status.setText("Folder configured.");
        } catch (Exception ex) {
            status.setText("Folder error: " + ex.getMessage());
        }
    }

    private void receive() {
        if (!service.configured()) { status.setText("Choose a folder first."); return; }
        try {
            int quantity = Integer.parseInt(qty.getText().trim());
            java.util.Map<String, Object> result = service.receive(scan.getText().trim(), quantity, false);
            if (Boolean.TRUE.equals(result.get("needsRegistration"))) {
                String gtin = String.valueOf(result.get("gtin"));
                String name = JOptionPane.showInputDialog(this, "Unknown product " + gtin + ". Product name:");
                if (name == null || name.trim().length() == 0) return;
                service.registerProduct(gtin, name.trim(), "", "", 0.0, -1, "", "MANUAL");
                service.receive(scan.getText().trim(), quantity, true);
            }
            scan.setText("");
            refresh();
            status.setText("Received.");
        } catch (Exception ex) {
            status.setText("Error: " + ex.getMessage());
        }
    }

    private void exportReport() {
        if (!service.configured()) { status.setText("Choose a folder first."); return; }
        try {
            Instant now = Instant.now();
            ManagementReport.Result r = ManagementReport.write(service.store().getSharedRoot().resolve("reports"),
                    service.dashboard(now), service.reorder(now), service.stock(), now);
            status.setText("Report written: " + r.html.getFileName());
        } catch (Exception ex) {
            status.setText("Report error: " + ex.getMessage());
        }
    }

    private void refresh() {
        model.setRowCount(0);
        for (StockLine line : service.stock()) if (line.active) model.addRow(stockRow(line));
        DashboardMetrics m = service.dashboard(Instant.now());
        kpis.setText("SKUs " + m.distinctSkus + "  |  Value " + String.format("%.2f", m.onHandValue)
                + "  |  Expired " + m.expired + "  |  Expiring 30d " + m.expiring30 + "  |  Out " + m.outOfStock);
    }
}
```

- [ ] **Step 4: Route `--classic-ui` in `MedicalSupplyApp`**

Replace the `--classic-ui` branch in `MedicalSupplyApp.main` with:

```java
        if (args.length > 0 && "--classic-ui".equals(args[0])) {
            try {
                AppConfig config = AppConfig.load();
                GudidClient gudid = config.gudidEnabled
                        ? new GudidClient(config.gudidEndpoint, new HttpsFetcher()) : null;
                SwingApp.launch(new AppService(config, gudid));
            } catch (Exception ex) {
                System.err.println("Classic UI failed: " + ex.getMessage());
                System.exit(1);
            }
            return;
        }
```

- [ ] **Step 5: Build and verify**

Run: `powershell -File apps/medical-supply-java/build.ps1`
Expected: `SwingRowsTest: PASS` and all others green. (The frame itself is verified manually: `java -jar dist/MedicalSupply-RC.jar --classic-ui`.)

- [ ] **Step 6: Commit**

```bash
git add apps/medical-supply-java/src/main/java/org/medsupply/SwingApp.java apps/medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java apps/medical-supply-java/src/test/java/org/medsupply/SwingRowsTest.java
git commit -m "feat(medsupply): Swing classic-ui fallback"
```

---

### Task 3: React/MUI SPA + QR labels + build integration

> **Scope note:** This task delivers a *complete, buildable* SPA scaffold — build config, API client, theme, drawer navigation, and working Dashboard / Scan / Inventory / Registration / Labels / Diagnostics workspaces wired to `/api/*`. Visual refinement (spacing, empty states, richer Rapid Scan interactions) is expected to continue iteratively with the `frontend-design` skill against the running app; the scaffold here is functional, not final. Every screen's data and actions use only the Plan 3 API contract, which is already tested.

**Files:**
- Create: `apps/medical-supply-java/frontend/package.json`, `vite.config.js`, `index.html`
- Create: `apps/medical-supply-java/frontend/src/api.js`, `theme.js`, `main.jsx`
- Delete: `apps/medical-supply-java/src/main/resources/web/index.html`, `apps/medical-supply-java/src/main/resources/web/app.js` (replaced by the Vite build output)
- Modify: `apps/medical-supply-java/.gitignore` (ignore generated `src/main/resources/web/`)
- Modify: `apps/medical-supply-java/build.ps1` (npm frontend build)

**Interfaces:**
- Consumes: the Plan 3 `/api/*` endpoints (`GET /api/state`; POST `/api/configure|receive|pick|adjust|archive|register|gudid|report|shutdown`).
- Produces: static assets under `src/main/resources/web/` (generated) that `BrowserServer` serves; a browser SPA with the six workspaces and QR label printing.

- [ ] **Step 1: Create the frontend package + build config**

`apps/medical-supply-java/frontend/package.json`:

```json
{
  "name": "medical-supply-ui",
  "private": true,
  "type": "module",
  "scripts": { "build": "vite build", "dev": "vite" },
  "dependencies": {
    "@emotion/react": "^11.14.0",
    "@emotion/styled": "^11.14.0",
    "@fontsource/roboto": "^5.2.6",
    "@mui/icons-material": "^7.3.1",
    "@mui/material": "^7.3.1",
    "qrcode": "^1.5.4",
    "react": "^19.1.1",
    "react-dom": "^19.1.1"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.4",
    "vite": "^7.1.3"
  }
}
```

`apps/medical-supply-java/frontend/vite.config.js`:

```javascript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: path.resolve(here, '../src/main/resources/web'),
    emptyOutDir: true,
    assetsInlineLimit: 4096,
    target: ['chrome100', 'edge100'],
    sourcemap: false
  }
})
```

`apps/medical-supply-java/frontend/index.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="theme-color" content="#6264a7" />
    <link rel="icon" href="data:," />
    <title>Medical Supply Tracking</title>
  </head>
  <body>
    <div id="root"></div>
    <script>window.__MEDSUPPLY_TOKEN__ = "__SESSION_TOKEN__";</script>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

- [ ] **Step 2: Create the API client and theme**

`apps/medical-supply-java/frontend/src/api.js`:

```javascript
const token = window.__MEDSUPPLY_TOKEN__

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    cache: 'no-store',
    headers: { 'Content-Type': 'application/json', 'X-Session-Token': token, ...(options.headers || {}) }
  })
  const body = await response.json()
  if (!response.ok) throw new Error(body.message || `Request failed (${response.status})`)
  return body
}

const post = (path, payload) => request(path, { method: 'POST', body: JSON.stringify(payload || {}) })

export const api = {
  state: () => request('/api/state'),
  configure: payload => post('/api/configure', payload),
  receive: payload => post('/api/receive', payload),
  pick: payload => post('/api/pick', payload),
  adjust: payload => post('/api/adjust', payload),
  archive: payload => post('/api/archive', payload),
  register: payload => post('/api/register', payload),
  gudid: payload => post('/api/gudid', payload),
  report: () => post('/api/report', {}),
  shutdown: () => post('/api/shutdown', {})
}
```

`apps/medical-supply-java/frontend/src/theme.js`:

```javascript
import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    primary: { main: '#6264a7' },
    error: { main: '#b91c1c' },
    warning: { main: '#d97706' },
    background: { default: '#f5f5f5' }
  },
  shape: { borderRadius: 8 },
  typography: { fontFamily: 'Roboto, Segoe UI, Arial, sans-serif' }
})
```

- [ ] **Step 3: Create the SPA (`main.jsx`)**

`apps/medical-supply-java/frontend/src/main.jsx` — drawer navigation with the six workspaces. Dashboard, Scan, Inventory, Registration, Labels, and Diagnostics are all functional against `/api/*`. Labels render QR codes client-side (offline) via the bundled `qrcode` library and print with `window.print()`.

```jsx
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  AppBar, Box, Button, Card, CardContent, Chip, CssBaseline, Divider, Drawer, IconButton,
  List, ListItemButton, ListItemIcon, ListItemText, Snackbar, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, ThemeProvider, Toolbar, Typography
} from '@mui/material'
import DashboardRounded from '@mui/icons-material/DashboardRounded'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded'
import AppRegistrationRounded from '@mui/icons-material/AppRegistrationRounded'
import QrCode2Rounded from '@mui/icons-material/QrCode2Rounded'
import TroubleshootRounded from '@mui/icons-material/TroubleshootRounded'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import QRCode from 'qrcode'
import { api } from './api'
import { theme } from './theme'

const DRAWER = 240
const NAV = [
  ['dashboard', 'Dashboard', <DashboardRounded />],
  ['scan', 'Scan', <QrCodeScannerRounded />],
  ['inventory', 'Inventory', <Inventory2Rounded />],
  ['registration', 'Registration', <AppRegistrationRounded />],
  ['labels', 'Labels', <QrCode2Rounded />],
  ['diagnostics', 'Diagnostics', <TroubleshootRounded />]
]

function expiryColor(iso) {
  if (!iso) return 'default'
  const days = (new Date(iso) - new Date()) / 86400000
  if (days < 0) return 'error'
  if (days <= 7) return 'error'
  if (days <= 30) return 'warning'
  return 'default'
}

function App() {
  const [view, setView] = useState('dashboard')
  const [state, setState] = useState(null)
  const [toast, setToast] = useState('')

  const refresh = useCallback(async () => {
    try { setState(await api.state()) } catch (e) { setToast(e.message) }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15000)
    return () => clearInterval(id)
  }, [refresh])

  const run = useCallback(async (fn, ok) => {
    try { await fn(); if (ok) setToast(ok); await refresh() }
    catch (e) { setToast(e.message) }
  }, [refresh])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppBar position="fixed" sx={{ zIndex: t => t.zIndex.drawer + 1 }}>
        <Toolbar><Typography variant="h6">Medical Supply Tracking</Typography></Toolbar>
      </AppBar>
      <Drawer variant="permanent" sx={{ width: DRAWER, '& .MuiDrawer-paper': { width: DRAWER } }}>
        <Toolbar />
        <List>
          {NAV.map(([key, label, icon]) => (
            <ListItemButton key={key} selected={view === key} onClick={() => setView(key)}>
              <ListItemIcon>{icon}</ListItemIcon>
              <ListItemText primary={label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ ml: `${DRAWER}px`, p: 3, mt: 8 }}>
        {!state ? <Typography>Loading…</Typography> : !state.configured && view !== 'diagnostics'
          ? <FolderPrompt run={run} state={state} />
          : {
              dashboard: <Dashboard state={state} run={run} />,
              scan: <Scan run={run} refresh={refresh} setToast={setToast} />,
              inventory: <Inventory state={state} run={run} />,
              registration: <Registration state={state} run={run} />,
              labels: <Labels state={state} />,
              diagnostics: <Diagnostics state={state} run={run} />
            }[view]}
      </Box>
      <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast('')} message={toast} />
    </ThemeProvider>
  )
}

function FolderPrompt({ run, state }) {
  const [path, setPath] = useState(state.sharedRoot || '')
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Synchronized folder</Typography>
      <Stack direction="row" spacing={1}>
        <TextField fullWidth size="small" label="OneDrive folder path" value={path}
          onChange={e => setPath(e.target.value)} />
        <Button variant="contained" onClick={() => run(() => api.configure({ sharedRoot: path }), 'Folder set')}>Set</Button>
      </Stack>
    </CardContent></Card>
  )
}

function Dashboard({ state, run }) {
  const d = state.dashboard || {}
  const tiles = [
    ['SKUs', d.distinctSkus], ['On-hand value', (d.onHandValue || 0).toFixed(2)],
    ['Expired', d.expired], ['Expiring 7d', d.expiring7], ['Expiring 30d', d.expiring30],
    ['Out of stock', d.outOfStock], ['Stale', d.stale]
  ]
  const reorder = (state.reorder || []).filter(r => r.needsReorder)
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
        {tiles.map(([label, value]) => (
          <Card key={label} sx={{ minWidth: 140 }}><CardContent>
            <Typography variant="h4">{value ?? 0}</Typography>
            <Typography variant="body2" color="text.secondary">{label}</Typography>
          </CardContent></Card>
        ))}
      </Box>
      <Button variant="outlined" sx={{ alignSelf: 'flex-start' }}
        onClick={() => run(() => api.report(), 'Report exported')}>Export management report</Button>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Reorder needed</Typography>
        <Table size="small">
          <TableHead><TableRow><TableCell>Product</TableCell><TableCell>On hand</TableCell>
            <TableCell>Target</TableCell><TableCell>Order</TableCell><TableCell>Est. cost</TableCell></TableRow></TableHead>
          <TableBody>
            {reorder.map(r => (
              <TableRow key={r.gtin}><TableCell>{r.name || r.gtin}</TableCell><TableCell>{r.onHand}</TableCell>
                <TableCell>{r.parProvided ? r.par : r.suggestedPar}</TableCell><TableCell>{r.suggestedOrderQty}</TableCell>
                <TableCell>{(r.estimatedCost || 0).toFixed(2)}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent></Card>
    </Stack>
  )
}

function Scan({ run, refresh, setToast }) {
  const [raw, setRaw] = useState('')
  const [qty, setQty] = useState('1')
  const submit = async () => {
    try {
      const result = await api.receive({ raw, quantity: qty, force: 'false' })
      if (result.needsRegistration) {
        const s = result.suggestion || {}
        const name = window.prompt(`Unknown product ${result.gtin}. Product name:`, s.name || '')
        if (!name) return
        await api.register({ gtin: result.gtin, name, manufacturer: s.manufacturer || '',
          category: s.category || '', source: s.found ? 'GUDID' : 'MANUAL' })
        await api.receive({ raw, quantity: qty, force: 'true' })
      }
      setRaw(''); setToast('Received'); refresh()
    } catch (e) { setToast(e.message) }
  }
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Scan to receive</Typography>
      <Stack direction="row" spacing={1}>
        <TextField autoFocus fullWidth size="small" label="Barcode" value={raw}
          onChange={e => setRaw(e.target.value)} onKeyDown={e => e.key === 'Enter' && submit()} />
        <TextField size="small" label="Qty" type="number" value={qty} sx={{ width: 100 }}
          onChange={e => setQty(e.target.value)} />
        <Button variant="contained" onClick={submit}>Receive</Button>
      </Stack>
    </CardContent></Card>
  )
}

function Inventory({ state, run }) {
  const [filter, setFilter] = useState('')
  const rows = (state.stock || []).filter(l => l.active &&
    (l.name + l.gtin + l.lot).toLowerCase().includes(filter.toLowerCase()))
  return (
    <Card><CardContent>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <TextField size="small" label="Search" value={filter} onChange={e => setFilter(e.target.value)} />
      </Stack>
      <Table size="small">
        <TableHead><TableRow><TableCell>Name</TableCell><TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
          <TableCell>Qty</TableCell><TableCell>Actions</TableCell></TableRow></TableHead>
        <TableBody>
          {rows.map(l => (
            <TableRow key={l.itemKey}>
              <TableCell>{l.name || l.gtin}</TableCell><TableCell>{l.lot}</TableCell>
              <TableCell><Chip size="small" color={expiryColor(l.expirationIso)} label={l.expirationIso || '—'} /></TableCell>
              <TableCell>{l.quantity}</TableCell>
              <TableCell>
                <Button size="small" onClick={() => { const n = window.prompt('Pick quantity', '1'); if (n) run(() => api.pick({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, quantity: n }), 'Picked') }}>Pick</Button>
                <Button size="small" onClick={() => { const n = window.prompt('Set quantity', String(l.quantity)); if (n !== null) run(() => api.adjust({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, quantity: n }), 'Adjusted') }}>Adjust</Button>
                <Button size="small" color="error" onClick={() => { const r = window.prompt('Archive reason'); if (r) run(() => api.archive({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, reason: r }), 'Archived') }}>Archive</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </CardContent></Card>
  )
}

function Registration({ state, run }) {
  const [form, setForm] = useState({ gtin: '', name: '', manufacturer: '', category: '', unitPrice: '', par: '', notes: '' })
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const lookup = async () => {
    try {
      const r = await api.gudid({ gtin: form.gtin })
      if (r.enabled && r.found) setForm(f => ({ ...f, name: r.name || f.name, manufacturer: r.manufacturer || f.manufacturer, category: r.category || f.category }))
    } catch (e) { /* offline is fine */ }
  }
  const categories = [...new Set((state.catalog || []).map(c => c.category).filter(Boolean))]
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Register / update product</Typography>
      <Stack spacing={1} sx={{ maxWidth: 520 }}>
        <Stack direction="row" spacing={1}>
          <TextField fullWidth size="small" label="GTIN" value={form.gtin} onChange={e => set('gtin', e.target.value)} />
          <Button onClick={lookup}>GUDID lookup</Button>
        </Stack>
        <TextField size="small" label="Name" value={form.name} onChange={e => set('name', e.target.value)} />
        <TextField size="small" label="Manufacturer" value={form.manufacturer} onChange={e => set('manufacturer', e.target.value)} />
        <TextField size="small" label="Category" value={form.category} onChange={e => set('category', e.target.value)}
          helperText={categories.length ? `Existing: ${categories.join(', ')}` : ''} />
        <Stack direction="row" spacing={1}>
          <TextField size="small" label="Unit price" value={form.unitPrice} onChange={e => set('unitPrice', e.target.value)} />
          <TextField size="small" label="PAR (blank = none)" value={form.par} onChange={e => set('par', e.target.value)} />
        </Stack>
        <TextField size="small" label="Notes" value={form.notes} onChange={e => set('notes', e.target.value)} />
        <Button variant="contained" onClick={() => run(() => api.register({
          ...form, unitPrice: form.unitPrice || '0', par: form.par === '' ? '-1' : form.par, source: 'MANUAL'
        }), 'Saved')}>Save product</Button>
      </Stack>
    </CardContent></Card>
  )
}

function Labels({ state }) {
  const [urls, setUrls] = useState({})
  const rows = (state.stock || []).filter(l => l.active)
  useEffect(() => {
    let cancelled = false
    Promise.all(rows.map(l => QRCode.toDataURL(l.barcode || l.gtin, { margin: 1, width: 96 })
      .then(url => [l.itemKey, url]))).then(pairs => { if (!cancelled) setUrls(Object.fromEntries(pairs)) })
    return () => { cancelled = true }
  }, [state])
  return (
    <Box>
      <Button variant="outlined" sx={{ mb: 2 }} onClick={() => window.print()}>Print labels</Button>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 1 }}>
        {rows.map(l => (
          <Card key={l.itemKey} variant="outlined"><CardContent sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            {urls[l.itemKey] && <img src={urls[l.itemKey]} width="72" height="72" alt="QR" />}
            <Box>
              <Typography variant="body2" fontWeight={600}>{l.name || l.gtin}</Typography>
              <Typography variant="caption" display="block">Lot: {l.lot}</Typography>
              <Typography variant="caption" display="block">Exp: {l.expirationIso || '—'} · Qty: {l.quantity}</Typography>
            </Box>
          </CardContent></Card>
        ))}
      </Box>
    </Box>
  )
}

function Diagnostics({ state, run }) {
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Diagnostics</Typography>
      <Typography variant="body2">Shared root: {state.sharedRoot || '(not set)'}</Typography>
      <Typography variant="body2">Events: {state.eventCount} · Pending: {state.pendingCount}</Typography>
      <Typography variant="body2">GUDID enabled: {String(state.gudidEnabled)}</Typography>
      <Typography variant="body2" color="error" sx={{ whiteSpace: 'pre-wrap' }}>
        {(state.errors || []).join('\n')}
      </Typography>
    </CardContent></Card>
  )
}

createRoot(document.getElementById('root')).render(<App />)
```

- [ ] **Step 4: Remove Plan 3's minimal UI and ignore generated output**

Delete `apps/medical-supply-java/src/main/resources/web/index.html` and `apps/medical-supply-java/src/main/resources/web/app.js` (the Vite build regenerates `web/`). Add to `apps/medical-supply-java/.gitignore`:

```gitignore
/src/main/resources/web/
```

- [ ] **Step 5: Add the frontend build to `build.ps1`**

In `apps/medical-supply-java/build.ps1`, add a `-SkipFrontend` switch to the `param(...)` block, and before the main-compilation block insert:

```powershell
if (-not $SkipFrontend) {
    $frontendRoot = Join-Path $projectRoot "frontend"
    if (-not (Test-Path (Join-Path $frontendRoot "node_modules"))) {
        Push-Location $frontendRoot
        try { & npm install; if ($LASTEXITCODE -ne 0) { throw "npm install failed." } } finally { Pop-Location }
    }
    Push-Location $frontendRoot
    try { & npm run build; if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." } } finally { Pop-Location }
}
```

(The existing resource-copy step then bundles the built `web/` into the JAR. Use `-SkipFrontend` when iterating on Java only, provided `src/main/resources/web/` already contains a prior build.)

- [ ] **Step 6: Build the whole app and verify it serves the SPA**

Run:
```
cd apps/medical-supply-java/frontend; npm install; cd ..
powershell -File apps/medical-supply-java/build.ps1
```
Expected: Vite build writes `src/main/resources/web/index.html` + `assets/*`; Java tests pass; `Built: ...`. Then launch `java -jar dist/MedicalSupply-RC.jar`, set a folder, scan, and confirm the Dashboard/Inventory/Labels workspaces render.

- [ ] **Step 7: Commit**

```bash
git add apps/medical-supply-java/frontend apps/medical-supply-java/.gitignore apps/medical-supply-java/build.ps1
git rm apps/medical-supply-java/src/main/resources/web/index.html apps/medical-supply-java/src/main/resources/web/app.js
git commit -m "feat(medsupply): React/MUI SPA with QR labels and Vite build"
```

---

### Task 4: Release & qualification packaging

**Files:**
- Create: `apps/medical-supply-java/README.md`, `TESTING.md`, `RELEASE_NOTES.md`
- Create: `apps/medical-supply-java/qualification/browser-smoke-evidence.md`
- Modify: `apps/medical-supply-java/build.ps1` (copy docs + qualification into `dist`)
- Modify: `apps/medical-supply-java/run-medical-supply.cmd` (document `--classic-ui`)

**Interfaces:**
- Produces: a self-describing `dist/` matching the commercial-tracking release shape (JAR, launcher, README, TESTING, RELEASE_NOTES, qualification/).

- [ ] **Step 1: Write `README.md`**

`apps/medical-supply-java/README.md`:

```markdown
# Medical Supply Tracking

Portable Java 8 medical-supply inventory tracker using a OneDrive-synchronized Teams/SharePoint
folder as an immutable event store. Mirrors the original PowerApps inventory tracker: scan-to-stock,
batch receiving, browse/search with expiry alerts, FDA GUDID-backed product registration, a
management dashboard with PAR/consumption reorder alerts, and printable QR labels.

The default interface is a precompiled React/MUI app served by Java over a loopback-only HTTP
endpoint; the SPA (React, MUI, fonts, QR generation) is embedded in the JAR. Workstations need only
Java 8 and a browser. Node/npm are development-time tools only.

## Build
Requires JDK 9+ (to compile `--release 8`), Node/npm (frontend), and PowerShell. No Maven/Gradle.

    .\build.ps1                 # full build (frontend + Java + tests + jar)
    .\build.ps1 -SkipFrontend   # Java only, reuse existing web assets

Output: `dist/MedicalSupply-RC.jar`, `run-medical-supply.cmd`, docs, `qualification/`.

## Run
Copy `dist` to a workstation and launch `run-medical-supply.cmd`. On first run, select the
synchronized folder. The service binds `127.0.0.1` with an ephemeral port and a random session token.

Classic Swing fallback: `run-medical-supply.cmd --classic-ui`.

## FDA GUDID
Unknown GTINs are looked up best-effort against the AccessGUDID Device Lookup API
(`https://accessgudid.nlm.nih.gov/api/v3/devices/lookup.json`). Lookups are optional; registration
works fully offline, and confirmed products are cached in the shared catalog for all workstations.
Disable lookups in per-user settings for air-gapped sites.

## Data
Shared events under the selected root; per-user settings and pending files under
`%LOCALAPPDATA%\MedicalSupply`. The shared store contains no credentials.
```

- [ ] **Step 2: Write `TESTING.md` and `RELEASE_NOTES.md`**

`apps/medical-supply-java/TESTING.md`:

```markdown
# Testing

`.\build.ps1` compiles with `javac --release 8` and runs every `*Test` class. A green build prints
`<Name>Test: PASS` for each and `Built: ...`.

Headless self-test (no browser): `java -jar dist\MedicalSupply-RC.jar --self-test` prints
`MedicalSupply self-test: PASS`.

Browser smoke: launch the JAR, set a synchronized folder, scan a GS1 barcode, confirm the item
appears in Inventory with the correct expiry color, export a management report, and print labels.
Record results in `qualification/browser-smoke-evidence.md`.
```

`apps/medical-supply-java/RELEASE_NOTES.md`:

```markdown
# Release Notes

## 0.1.0
- Event-sourced medical-supply tracker on a OneDrive-synchronized folder (Java 8, no Maven/Gradle).
- GS1 barcode decoding (AI 01/17/10/21/30) with FNC1 handling.
- Catalog + inventory projections; expiry color coding; PAR + consumption reorder advisor.
- FDA GUDID-backed registration (offline-first, best-effort, cached in the shared catalog).
- Management dashboard and exportable HTML/CSV/PDF report.
- React/MUI browser UI with QR label printing; Swing `--classic-ui` fallback.
```

- [ ] **Step 3: Write the qualification evidence template**

`apps/medical-supply-java/qualification/browser-smoke-evidence.md`:

```markdown
# Browser Smoke Evidence

| Step | Expected | Result | Notes |
|------|----------|--------|-------|
| Launch JAR | Prints `Medical Supply UI: http://127.0.0.1:<port>`, browser opens | | |
| Set folder | Dashboard renders, no errors | | |
| Scan known GTIN | Item received, appears in Inventory | | |
| Scan unknown GTIN | Registration prompt (GUDID prefill when online) | | |
| Expiry coloring | ≤7d red, ≤30d amber | | |
| Export report | HTML/CSV/PDF written under `reports/` | | |
| Print labels | QR labels render and print | | |
| `--classic-ui` | Swing window works offline | | |

Java version: ______  Date: ______  Tester: ______
```

- [ ] **Step 4: Copy docs + qualification into `dist` in `build.ps1`**

In `apps/medical-supply-java/build.ps1`, after `Copy-Item -LiteralPath (Join-Path $projectRoot "run-medical-supply.cmd") -Destination $dist`, add:

```powershell
foreach ($doc in @("README.md", "TESTING.md", "RELEASE_NOTES.md")) {
    $p = Join-Path $projectRoot $doc
    if (Test-Path $p) { Copy-Item -LiteralPath $p -Destination $dist }
}
$qual = Join-Path $projectRoot "qualification"
if (Test-Path $qual) { Copy-Item -LiteralPath $qual -Destination $dist -Recurse }
```

- [ ] **Step 5: Document `--classic-ui` in the launcher**

Replace `apps/medical-supply-java/run-medical-supply.cmd` with:

```bat
@echo off
REM Medical Supply Tracking launcher.
REM   run-medical-supply.cmd              Browser UI (default)
REM   run-medical-supply.cmd --classic-ui Swing desktop fallback
setlocal
set DIR=%~dp0
java -jar "%DIR%MedicalSupply-RC.jar" %*
if errorlevel 1 pause
endlocal
```

- [ ] **Step 6: Full build and package**

Run: `powershell -File apps/medical-supply-java/build.ps1`
Expected: green tests, and `dist/` contains `MedicalSupply-RC.jar`, `run-medical-supply.cmd`, `README.md`, `TESTING.md`, `RELEASE_NOTES.md`, and `qualification/`.

- [ ] **Step 7: Commit**

```bash
git add apps/medical-supply-java/README.md apps/medical-supply-java/TESTING.md apps/medical-supply-java/RELEASE_NOTES.md apps/medical-supply-java/qualification apps/medical-supply-java/build.ps1 apps/medical-supply-java/run-medical-supply.cmd
git commit -m "docs(medsupply): release and qualification packaging"
```

---

## Self-Review

**Spec coverage (Plan 4 scope):**
- Management-report PDF (§6.3, "PDF/HTML/CSV") → Task 1. ✓
- Swing `--classic-ui` fallback (§7 UI) → Task 2. ✓
- React/MUI SPA replacing the minimal UI; Dashboard, Scan, Inventory (search + expiry color + pick/adjust/archive), Registration (GUDID prefill + category pick-list hint), Labels (locally-generated QR + print), Diagnostics (§6.1, §7 workspaces, §5 GUDID, managed category list) → Task 3. ✓
- Offline QR generation via bundled `qrcode` lib, no external calls (§7 Labels) → Task 3. ✓
- README/TESTING/RELEASE_NOTES + `dist-review/qualification` browser-smoke evidence; frontend npm step in `build.ps1` (§8) → Tasks 3–4. ✓
- **Deferred/iterative:** SPA visual polish and richer Rapid Scan batch interactions (call out in Task 3's scope note) are continued with the `frontend-design` skill against the running app — the data/actions are complete, the styling is a baseline. Rapid Scan's multi-item batch UI is representable today by repeated Scan receives; a dedicated batch screen is a fast follow.

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"similar to Task N". Java steps carry complete code; the SPA is complete, runnable JSX (not stubs). ✓

**Type/contract consistency:** The SPA calls exactly the Plan 3 endpoints and payload keys (`raw/quantity/force`, `gtin/lot/expirationIso/quantity`, `gtin/name/manufacturer/category/unitPrice/par/notes/source`); response keys read (`dashboard`, `stock`, `reorder`, `catalog`, `sharedRoot`, `eventCount`, `pendingCount`, `gudidEnabled`, `errors`, `needsRegistration`, `suggestion`) match `AppService.snapshot`/`receive` from Plan 3. `ManagementReport.Result` gains `pdf` consistently across Task 1's edits and the `/api/report` response. `SwingApp.stockRow` matches its test. `PortablePdf.write` matches `PortablePdfTest` and the `ManagementReport` call. ✓

**Note on Plan 2 GS1 finding (from the compliance review):** independent of this plan, `Gs1Parser`'s no-separator fallback can misparse a variable field embedding `10/21/30`. Recommended as a small standalone fix (tighten the plausibility guard for those AIs and/or set `requiresConfirmation`); not required for Plan 4 but tracked here so it is not lost.

## Execution Handoff

Plan 4 completes the 4-plan sequence (Foundation → Domain & Analytics → UI Server → Presentation). At its end the app is feature-complete against the spec: browser SPA + Swing fallback, dashboard + reorder alerts, GUDID registration, QR labels, HTML/CSV/PDF reports, and a packaged, qualifiable `dist/`.

Two execution options:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, review between tasks. (Task 3 benefits from a follow-up `frontend-design` pass.)
2. **Inline Execution** — execute tasks in this session with checkpoints.
