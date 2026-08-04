# Medical Supply Tracking — Plan 3: UI Application Service & HTTP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Plan 2 domain into a runnable application: a shared headless `AppService` (used by every front end), an exportable management report, a loopback HTTP API server with a minimal bundled browser UI, and the build wiring to package web resources.

**Architecture:** `AppService` wraps `EventStore` + `Projection` + `InventoryAnalytics` + `ReorderAdvisor` + `GudidClient` behind plain Java methods and a `snapshot()` map — this is the single brain both the browser API and (Plan 4) the Swing UI call, and it is fully unit-testable with no HTTP. `ManagementReport` renders the dashboard/reorder data to HTML + CSV under the shared `reports/` folder. `BrowserServer` (ported from `commercial-tracking-java`) exposes `AppService` over `127.0.0.1` with an ephemeral port, a random session token, and a static handler serving a minimal plain-HTML/JS UI embedded in the JAR. No React build yet — Plan 4 replaces the bundled UI with the MUI SPA and adds the Swing fallback, QR labels, PDF, and qualification packaging.

**Tech Stack:** Java 8 (`javac --release 8`), `com.sun.net.httpserver` (JDK built-in), the Plan 1 `Json` library. No third-party libraries; no npm in this plan.

## Global Constraints

- Target Java 8 bytecode (`javac --release 8`), Java SE + `com.sun.net.httpserver` only. No third-party libraries. No npm/Node in this plan.
- Package root: `org.medsupply`. Build/test via `medical-supply-java/build.ps1` (auto-discovers `*Test`). This plan adds a **resource-copy step** to `build.ps1` (Task 6) so `src/main/resources/web/**` is bundled into the JAR.
- **Formatting: conventional, readable, multi-line Java** — one statement per line, standard indentation, matching `commercial-tracking-java`. Do not minify.
- Depends on Plan 1 + Plan 2 types (built and verified): `AppConfig`, `EventStore`, `Json`, `SupplyEvent`, `SupplyEvents` (+ `Identity`), `ItemKey`, `Gs1Parser`/`Gs1Scan`, `Projection`/`CatalogProduct`/`StockLine`, `InventoryAnalytics`/`DashboardMetrics`, `ReorderAdvisor`/`ReorderSuggestion`/`Params`, `GudidClient`/`GudidResult`.
- Tests are framework-free `*Test` classes with `public static void main(String[])` that throw `AssertionError` on failure and print `XxxTest: PASS`.
- Time is injected into read-model methods (`Instant now`) for deterministic tests. Event `occurredUtc` is stamped from `Instant.now()` only inside `AppService` mutation methods (acceptable; those are not asserted on exact time).
- Security: the HTTP server binds `127.0.0.1` only, requires header `X-Session-Token` on `/api/*`, and rejects a mismatched `Origin`.

---

### Task 1: AppService core — configure, reload, snapshot

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/AppService.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/AppServiceTest.java`

**Interfaces:**
- Consumes: `AppConfig`, `EventStore`, `Projection`, `InventoryAnalytics`, `ReorderAdvisor`, `GudidClient`, `SupplyEvents` (Plans 1–2).
- Produces:
  - `new AppService(AppConfig config, GudidClient gudid)` — `gudid` may be `null` (lookups disabled).
  - `boolean configured()`; `void configure(java.nio.file.Path root)` (write-probe, build `EventStore`, save config, reload); `void reload()`.
  - `java.util.List<StockLine> stock()`, `java.util.Map<String,CatalogProduct> catalog()`, `java.util.List<String> errors()`.
  - `DashboardMetrics dashboard(java.time.Instant now)`; `java.util.List<ReorderSuggestion> reorder(java.time.Instant now)`.
  - `java.util.Map<String,Object> snapshot(java.time.Instant now)` — the JSON-ready state map (see code).
  - `SupplyEvents.Identity identity()`; `EventStore store()` (package-visible for later tasks).

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/AppServiceTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public final class AppServiceTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-svc");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();
        AppService svc = new AppService(config, null);
        check(!svc.configured(), "not configured initially");

        svc.configure(base.resolve("shared"));
        check(svc.configured(), "configured after");

        // Seed via the store directly to prove reload + snapshot.
        SupplyEvents.Identity id = svc.identity();
        svc.store().append(SupplyEvents.productRegistered(id, "2026-08-01T00:00:00Z",
                "00380740000010", "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "MANUAL"));
        svc.store().append(SupplyEvents.stockReceived(id, "2026-08-02T00:00:00Z",
                "00380740000010", "L1", "2026-11-30", "bc", 2));
        svc.reload();

        check(svc.stock().size() == 1, "one stock line");
        check(svc.stock().get(0).quantity == 2, "qty 2");

        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        Map<String, Object> snap = svc.snapshot(now);
        check(Boolean.TRUE.equals(snap.get("configured")), "snap configured");
        Map<String, Object> metrics = Json.asMap(snap.get("dashboard"));
        check("1".equals(Json.str(metrics, "distinctSkus")), "snap skus");
        check(Json.asList(snap.get("stock")).size() == 1, "snap stock");
        check(Json.asList(snap.get("reorder")).size() == 1, "snap reorder");
        System.out.println("AppServiceTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... AppService`.

- [ ] **Step 3: Write `AppService` (core)**

`medical-supply-java/src/main/java/org/medsupply/AppService.java`:

```java
package org.medsupply;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AppService {
    private final AppConfig config;
    private final GudidClient gudid;
    private EventStore store;
    private Projection projection = Projection.replay(new ArrayList<SupplyEvent>());
    private final List<SupplyEvent> events = new ArrayList<SupplyEvent>();
    private final List<String> errors = new ArrayList<String>();
    private String refreshedUtc = "";

    public AppService(AppConfig config, GudidClient gudid) {
        this.config = config;
        this.gudid = gudid;
        if (config.sharedRoot != null && Files.isDirectory(config.sharedRoot)) {
            try { openStore(config.sharedRoot); } catch (IOException ignored) { }
        }
    }

    public boolean configured() {
        return store != null;
    }

    public synchronized void configure(Path root) throws IOException {
        Files.createDirectories(root);
        Path probe = root.resolve(".medsupply-write-probe-" + UUID.randomUUID());
        try { Files.write(probe, new byte[] {1}); } finally { Files.deleteIfExists(probe); }
        openStore(root);
        config.sharedRoot = root.toAbsolutePath().normalize();
        config.save();
    }

    private void openStore(Path root) throws IOException {
        store = new EventStore(root, config.localRoot);
        reload();
    }

    public synchronized void reload() {
        events.clear();
        errors.clear();
        if (store == null) {
            projection = Projection.replay(events);
            return;
        }
        EventStore.LoadResult loaded = store.loadAll();
        events.addAll(loaded.events);
        errors.addAll(loaded.errors);
        projection = Projection.replay(events);
        refreshedUtc = Instant.now().toString();
    }

    public List<StockLine> stock() {
        return projection.stock();
    }

    public Map<String, CatalogProduct> catalog() {
        return projection.catalog();
    }

    public List<String> errors() {
        return new ArrayList<String>(errors);
    }

    public List<SupplyEvent> events() {
        return new ArrayList<SupplyEvent>(events);
    }

    public DashboardMetrics dashboard(Instant now) {
        return InventoryAnalytics.compute(projection.stock(), events, now, config.staleDays);
    }

    public List<ReorderSuggestion> reorder(Instant now) {
        return ReorderAdvisor.advise(projection.catalog(), projection.stock(), events, now,
                new ReorderAdvisor.Params(config.reorderWindowDays, config.reorderLeadDays,
                        config.reorderSafetyDays, config.reorderCoverageDays));
    }

    public SupplyEvents.Identity identity() {
        return new SupplyEvents.Identity(config.deviceId, config.actor, config.activeSessionId);
    }

    EventStore store() {
        return store;
    }

    GudidClient gudid() {
        return gudid;
    }

    AppConfig config() {
        return config;
    }

    public synchronized Map<String, Object> snapshot(Instant now) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("configured", Boolean.valueOf(store != null));
        value.put("sharedRoot", config.sharedRoot == null ? "" : config.sharedRoot.toString());
        value.put("deviceId", config.deviceId);
        value.put("actor", config.actor);
        value.put("gudidEnabled", Boolean.valueOf(config.gudidEnabled && gudid != null));
        value.put("eventCount", Integer.valueOf(events.size()));
        value.put("pendingCount", Integer.valueOf(store == null ? 0 : store.pendingCount()));
        value.put("refreshedUtc", refreshedUtc);
        value.put("dashboard", metricsMap(dashboard(now)));
        value.put("stock", stockMaps());
        value.put("catalog", catalogMaps());
        value.put("reorder", reorderMaps(now));
        value.put("errors", new ArrayList<String>(errors));
        return value;
    }

    static Map<String, Object> metricsMap(DashboardMetrics m) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("expired", Integer.valueOf(m.expired));
        value.put("expiring7", Integer.valueOf(m.expiring7));
        value.put("expiring30", Integer.valueOf(m.expiring30));
        value.put("outOfStock", Integer.valueOf(m.outOfStock));
        value.put("stale", Integer.valueOf(m.stale));
        value.put("distinctSkus", Integer.valueOf(m.distinctSkus));
        value.put("totalUnits", Integer.valueOf(m.totalUnits));
        value.put("onHandValue", Double.valueOf(m.onHandValue));
        value.put("activeEventsLast7", Integer.valueOf(m.activeEventsLast7));
        return value;
    }

    private List<Object> stockMaps() {
        List<Object> rows = new ArrayList<Object>();
        for (StockLine line : projection.stock()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("itemKey", line.itemKey);
            row.put("gtin", line.gtin);
            row.put("lot", line.lot);
            row.put("expirationIso", line.expirationIso);
            row.put("barcode", line.barcode);
            row.put("quantity", Integer.valueOf(line.quantity));
            row.put("active", Boolean.valueOf(line.active));
            row.put("name", line.name);
            row.put("manufacturer", line.manufacturer);
            row.put("category", line.category);
            row.put("unitPrice", Double.valueOf(line.unitPrice));
            row.put("par", Integer.valueOf(line.par));
            row.put("lastEventUtc", line.lastEventUtc);
            rows.add(row);
        }
        return rows;
    }

    private List<Object> catalogMaps() {
        List<Object> rows = new ArrayList<Object>();
        for (CatalogProduct p : projection.catalog().values()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("gtin", p.gtin);
            row.put("name", p.name);
            row.put("manufacturer", p.manufacturer);
            row.put("category", p.category);
            row.put("unitPrice", Double.valueOf(p.unitPrice));
            row.put("par", Integer.valueOf(p.par));
            row.put("notes", p.notes);
            row.put("source", p.source);
            rows.add(row);
        }
        return rows;
    }

    private List<Object> reorderMaps(Instant now) {
        List<Object> rows = new ArrayList<Object>();
        for (ReorderSuggestion s : reorder(now)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("gtin", s.gtin);
            row.put("name", s.name);
            row.put("onHand", Integer.valueOf(s.onHand));
            row.put("parProvided", Boolean.valueOf(s.parProvided));
            row.put("par", Integer.valueOf(s.par));
            row.put("avgDailyUsage", Double.valueOf(s.avgDailyUsage));
            row.put("reorderPoint", Integer.valueOf(s.reorderPoint));
            row.put("suggestedPar", Integer.valueOf(s.suggestedPar));
            row.put("suggestedOrderQty", Integer.valueOf(s.suggestedOrderQty));
            row.put("estimatedCost", Double.valueOf(s.estimatedCost));
            row.put("needsReorder", Boolean.valueOf(s.needsReorder));
            row.put("insufficientHistory", Boolean.valueOf(s.insufficientHistory));
            rows.add(row);
        }
        return rows;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `AppServiceTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/AppService.java medical-supply-java/src/test/java/org/medsupply/AppServiceTest.java
git commit -m "feat(medsupply): application service core (configure, reload, snapshot)"
```

---

### Task 2: AppService scan and stock mutations

**Files:**
- Modify: `medical-supply-java/src/main/java/org/medsupply/AppService.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/AppServiceStockTest.java`

**Interfaces:**
- Consumes: `Gs1Parser`/`Gs1Scan`, `SupplyEvents` (Plan 2).
- Produces (add to `AppService`):
  - `Gs1Scan scan(String raw)` — parse only.
  - `Map<String,Object> receive(String raw, int quantity, boolean force)` — parse; if GTIN unknown to the catalog and not `force`, return `{needsRegistration:true, gtin, lot, expirationIso, suggestion:{...}}` (suggestion from GUDID when available) without writing. Otherwise append `STOCK_RECEIVED`, reload, return `{ok:true, itemKey, message}`.
  - `Map<String,Object> pick(String gtin, String lot, String expirationIso, int quantity)`.
  - `Map<String,Object> adjust(String gtin, String lot, String expirationIso, int absoluteQuantity)`.
  - `Map<String,Object> archive(String gtin, String lot, String expirationIso, String reason)`.
  - Each mutation throws `AppService.BadRequest` (a public `RuntimeException`) on invalid input.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/AppServiceStockTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AppServiceStockTest {
    private static final char GS = (char) 29;

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-stock");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();
        AppService svc = new AppService(config, null);
        svc.configure(base.resolve("shared"));

        String raw = "0100380740000010" + "10L1" + GS + "17261130";
        Map<String, Object> unknown = svc.receive(raw, 5, false);
        check(Boolean.TRUE.equals(unknown.get("needsRegistration")), "unknown needs registration");
        check(svc.stock().isEmpty(), "nothing written yet");

        svc.registerProduct("00380740000010", "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "MANUAL");
        Map<String, Object> received = svc.receive(raw, 5, false);
        check(Boolean.TRUE.equals(received.get("ok")), "received ok");
        check(svc.stock().get(0).quantity == 5, "qty 5");

        svc.pick("00380740000010", "L1", "2026-11-30", 2);
        check(svc.stock().get(0).quantity == 3, "qty 3 after pick");

        svc.adjust("00380740000010", "L1", "2026-11-30", 9);
        check(svc.stock().get(0).quantity == 9, "qty 9 after adjust");

        svc.archive("00380740000010", "L1", "2026-11-30", "expired");
        check(!svc.stock().get(0).active, "archived");

        boolean threw = false;
        try { svc.receive("nonsense", 1, false); } catch (AppService.BadRequest ex) { threw = true; }
        check(threw, "bad barcode rejected");
        System.out.println("AppServiceStockTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

(`registerProduct` is added in Task 3; to keep Task 2 self-contained, this test also exercises it. If executing strictly task-by-task, implement `registerProduct` as part of Step 3 below — it is small — and Task 3 expands the catalog/GUDID surface.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... receive` / `BadRequest`.

- [ ] **Step 3: Add scan/stock methods to `AppService`**

Add these members to `AppService` (inside the class):

```java
    public static final class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    private void requireConfigured() {
        if (store == null) throw new BadRequest("Select a synchronized folder first.");
    }

    private static String nowIso() {
        return Instant.now().toString();
    }

    public Gs1Scan scan(String raw) {
        return Gs1Parser.parse(raw);
    }

    public synchronized Map<String, Object> receive(String raw, int quantity, boolean force) throws IOException {
        requireConfigured();
        if (quantity <= 0) throw new BadRequest("Quantity must be greater than zero.");
        Gs1Scan parsed = Gs1Parser.parse(raw);
        if (!parsed.success) throw new BadRequest("No GTIN was found in the barcode.");
        if (!force && !projection.catalog().containsKey(parsed.gtin)) {
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("needsRegistration", Boolean.TRUE);
            response.put("gtin", parsed.gtin);
            response.put("lot", parsed.lot);
            response.put("expirationIso", parsed.expirationIso);
            response.put("barcode", parsed.raw);
            response.put("suggestion", gudidSuggestion(parsed.gtin));
            return response;
        }
        store.append(SupplyEvents.stockReceived(identity(), nowIso(),
                parsed.gtin, parsed.lot, parsed.expirationIso, parsed.raw, quantity));
        reload();
        Map<String, Object> response = ok("Received " + quantity + " of " + parsed.gtin + ".");
        response.put("itemKey", parsed.itemKey());
        return response;
    }

    public synchronized Map<String, Object> pick(String gtin, String lot, String expirationIso, int quantity)
            throws IOException {
        requireConfigured();
        if (quantity <= 0) throw new BadRequest("Quantity must be greater than zero.");
        store.append(SupplyEvents.stockPicked(identity(), nowIso(), gtin, lot, expirationIso, quantity));
        reload();
        return ok("Picked " + quantity + ".");
    }

    public synchronized Map<String, Object> adjust(String gtin, String lot, String expirationIso, int absoluteQuantity)
            throws IOException {
        requireConfigured();
        if (absoluteQuantity < 0) throw new BadRequest("Quantity cannot be negative.");
        store.append(SupplyEvents.stockAdjusted(identity(), nowIso(), gtin, lot, expirationIso, absoluteQuantity));
        reload();
        return ok("Quantity set to " + absoluteQuantity + ".");
    }

    public synchronized Map<String, Object> archive(String gtin, String lot, String expirationIso, String reason)
            throws IOException {
        requireConfigured();
        if (reason == null || reason.trim().length() == 0) throw new BadRequest("A reason is required.");
        store.append(SupplyEvents.stockArchived(identity(), nowIso(), gtin, lot, expirationIso, reason.trim()));
        reload();
        return ok("Item archived.");
    }

    public synchronized Map<String, Object> registerProduct(String gtin, String name, String manufacturer,
            String category, double unitPrice, int par, String notes, String source) throws IOException {
        requireConfigured();
        if (gtin == null || gtin.trim().length() == 0) throw new BadRequest("GTIN is required.");
        if (name == null || name.trim().length() == 0) throw new BadRequest("Product name is required.");
        String type = projection.catalog().containsKey(gtin) ? "UPDATE" : "NEW";
        SupplyEvent event = "UPDATE".equals(type)
                ? SupplyEvents.productUpdated(identity(), nowIso(), gtin, name, manufacturer, category, unitPrice, par, notes, source)
                : SupplyEvents.productRegistered(identity(), nowIso(), gtin, name, manufacturer, category, unitPrice, par, notes, source);
        store.append(event);
        reload();
        return ok("NEW".equals(type) ? "Product registered." : "Product updated.");
    }

    private Object gudidSuggestion(String gtin) {
        if (gudid == null || !config.gudidEnabled) return new LinkedHashMap<String, Object>();
        GudidResult result = gudid.lookup(gtin);
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("found", Boolean.valueOf(result.found));
        value.put("name", result.suggestedName());
        value.put("manufacturer", result.companyName);
        value.put("category", result.suggestedCategory());
        return value;
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("ok", Boolean.TRUE);
        response.put("message", message);
        return response;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `AppServiceStockTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/AppService.java medical-supply-java/src/test/java/org/medsupply/AppServiceStockTest.java
git commit -m "feat(medsupply): scan, receive, pick, adjust, archive, register"
```

---

### Task 3: GUDID lookup passthrough

**Files:**
- Modify: `medical-supply-java/src/main/java/org/medsupply/AppService.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/AppServiceGudidTest.java`

**Interfaces:**
- Consumes: `GudidClient`/`GudidResult` (Plan 2).
- Produces (add to `AppService`): `Map<String,Object> lookupGudid(String gtin)` — returns `{enabled, found, name, manufacturer, category}`; `{enabled:false}` when disabled or the client is `null`.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/AppServiceGudidTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AppServiceGudidTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-gudid");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();

        GudidClient client = new GudidClient("https://x/api", new GudidClient.Fetcher() {
            public String fetch(String url) {
                return "{\"gudid\":{\"device\":{\"brandName\":\"Stent\",\"companyName\":\"Abbott\","
                        + "\"gmdnTerms\":{\"gmdn\":[{\"gmdnPTName\":\"Coronary stent\"}]}}}}";
            }
        });
        AppService svc = new AppService(config, client);
        Map<String, Object> r = svc.lookupGudid("00380740000010");
        check(Boolean.TRUE.equals(r.get("enabled")), "enabled");
        check(Boolean.TRUE.equals(r.get("found")), "found");
        check("Stent".equals(Json.str(r, "name")), "name");
        check("Abbott".equals(Json.str(r, "manufacturer")), "manufacturer");
        check("Coronary stent".equals(Json.str(r, "category")), "category");

        AppService disabled = new AppService(config, null);
        check(Boolean.FALSE.equals(disabled.lookupGudid("x").get("enabled")), "disabled when null");
        System.out.println("AppServiceGudidTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... lookupGudid`.

- [ ] **Step 3: Add `lookupGudid` to `AppService`**

```java
    public Map<String, Object> lookupGudid(String gtin) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        if (gudid == null || !config.gudidEnabled) {
            response.put("enabled", Boolean.FALSE);
            return response;
        }
        GudidResult result = gudid.lookup(gtin);
        response.put("enabled", Boolean.TRUE);
        response.put("found", Boolean.valueOf(result.found));
        response.put("name", result.suggestedName());
        response.put("manufacturer", result.companyName);
        response.put("category", result.suggestedCategory());
        return response;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `AppServiceGudidTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/AppService.java medical-supply-java/src/test/java/org/medsupply/AppServiceGudidTest.java
git commit -m "feat(medsupply): GUDID lookup passthrough in AppService"
```

---

### Task 4: Management report (HTML + CSV)

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/ManagementReport.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/ManagementReportTest.java`

**Interfaces:**
- Consumes: `StockLine`, `ReorderSuggestion`, `DashboardMetrics`.
- Produces:
  - `ManagementReport.Result` — public fields `java.nio.file.Path html; java.nio.file.Path csv;`.
  - `ManagementReport.write(java.nio.file.Path reportsDir, DashboardMetrics metrics, java.util.List<ReorderSuggestion> reorder, java.util.List<StockLine> stock, java.time.Instant now) -> Result` — writes `management-report-<utc>.html` and `-reorder-<utc>.csv` under `reportsDir`; HTML contains the KPI summary, the reorder table (needs-reorder rows), and an expiry list; CSV is the reorder list. All values HTML-escaped.
  - `ManagementReport.renderHtml(...)` and `renderReorderCsv(...)` — package-visible pure `String` builders (so tests assert content without touching disk).

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/ManagementReportTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ManagementReportTest {
    public static void main(String[] args) throws Exception {
        DashboardMetrics m = new DashboardMetrics();
        m.distinctSkus = 2;
        m.expired = 1;
        m.onHandValue = 123.5;

        List<ReorderSuggestion> reorder = new ArrayList<ReorderSuggestion>();
        ReorderSuggestion s = new ReorderSuggestion();
        s.gtin = "00380740000010";
        s.name = "Gauze <sterile>";
        s.onHand = 4;
        s.par = 10;
        s.parProvided = true;
        s.needsReorder = true;
        s.suggestedOrderQty = 6;
        s.estimatedCost = 12.0;
        reorder.add(s);

        List<StockLine> stock = new ArrayList<StockLine>();
        StockLine line = new StockLine();
        line.name = "Gauze";
        line.expirationIso = "2026-08-01";
        line.quantity = 4;
        line.active = true;
        stock.add(line);

        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        String html = ManagementReport.renderHtml(m, reorder, stock, now);
        check(html.contains("Gauze &lt;sterile&gt;"), "escaped name");
        check(html.contains("123.5") || html.contains("123.50"), "value shown");
        check(html.contains("Reorder"), "reorder section");

        String csv = ManagementReport.renderReorderCsv(reorder);
        check(csv.startsWith("gtin,name,onHand,par,suggestedOrderQty,estimatedCost"), "csv header");
        check(csv.contains("\"Gauze <sterile>\""), "csv quoted name");

        Path dir = Files.createTempDirectory("medsupply-report");
        ManagementReport.Result r = ManagementReport.write(dir, m, reorder, stock, now);
        check(Files.isRegularFile(r.html) && Files.isRegularFile(r.csv), "files written");
        System.out.println("ManagementReportTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... ManagementReport`.

- [ ] **Step 3: Write `ManagementReport`**

`medical-supply-java/src/main/java/org/medsupply/ManagementReport.java`:

```java
package org.medsupply;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class ManagementReport {
    private ManagementReport() {}

    public static final class Result {
        public final Path html;
        public final Path csv;
        Result(Path html, Path csv) { this.html = html; this.csv = csv; }
    }

    public static Result write(Path reportsDir, DashboardMetrics metrics, List<ReorderSuggestion> reorder,
            List<StockLine> stock, Instant now) throws IOException {
        Files.createDirectories(reportsDir);
        String stamp = now.toString().replaceAll("[^0-9]", "").substring(0, 14);
        Path html = reportsDir.resolve("management-report-" + stamp + ".html");
        Path csv = reportsDir.resolve("management-report-" + stamp + "-reorder.csv");
        Files.write(html, renderHtml(metrics, reorder, stock, now).getBytes(StandardCharsets.UTF_8));
        Files.write(csv, renderReorderCsv(reorder).getBytes(StandardCharsets.UTF_8));
        return new Result(html, csv);
    }

    static String renderHtml(DashboardMetrics m, List<ReorderSuggestion> reorder, List<StockLine> stock, Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'><title>Medical Supply Management Report</title>");
        sb.append("<style>body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2937}")
          .append("table{border-collapse:collapse;width:100%;margin:12px 0}")
          .append("th,td{border:1px solid #d1d5db;padding:6px 8px;text-align:left;font-size:13px}")
          .append("th{background:#f3f4f6}.kpi{display:inline-block;margin:6px 16px 6px 0}")
          .append(".urgent{color:#b91c1c;font-weight:600}</style></head><body>");
        sb.append("<h1>Medical Supply Management Report</h1>");
        sb.append("<div>Generated UTC: ").append(esc(now.toString())).append("</div>");
        sb.append("<h2>At a glance</h2>");
        kpi(sb, "SKUs", Integer.toString(m.distinctSkus));
        kpi(sb, "On-hand units", Integer.toString(m.totalUnits));
        kpi(sb, "On-hand value", money(m.onHandValue));
        kpi(sb, "Expired", Integer.toString(m.expired));
        kpi(sb, "Expiring &le;7d", Integer.toString(m.expiring7));
        kpi(sb, "Expiring &le;30d", Integer.toString(m.expiring30));
        kpi(sb, "Out of stock", Integer.toString(m.outOfStock));
        kpi(sb, "Stale", Integer.toString(m.stale));

        sb.append("<h2>Reorder</h2><table><tr><th>GTIN</th><th>Name</th><th>On hand</th><th>PAR / suggested</th>")
          .append("<th>Order qty</th><th>Est. cost</th><th>Basis</th></tr>");
        for (ReorderSuggestion s : reorder) {
            if (!s.needsReorder) continue;
            sb.append("<tr><td>").append(esc(s.gtin)).append("</td><td>").append(esc(s.name)).append("</td><td>")
              .append(s.onHand).append("</td><td>").append(s.parProvided ? Integer.toString(s.par) : Integer.toString(s.suggestedPar))
              .append("</td><td class='urgent'>").append(s.suggestedOrderQty).append("</td><td>").append(money(s.estimatedCost))
              .append("</td><td>").append(s.parProvided ? "PAR" : "Consumption").append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<h2>Expiry watch</h2><table><tr><th>Name</th><th>Expiration</th><th>Qty</th></tr>");
        for (StockLine line : stock) {
            if (!line.active || line.expirationIso.length() == 0) continue;
            LocalDate exp;
            try { exp = LocalDate.parse(line.expirationIso); } catch (RuntimeException ex) { continue; }
            if (exp.isAfter(today.plusDays(30))) continue;
            boolean urgent = !exp.isAfter(today.plusDays(7));
            sb.append("<tr><td>").append(esc(line.name)).append("</td><td").append(urgent ? " class='urgent'" : "")
              .append(">").append(esc(line.expirationIso)).append("</td><td>").append(line.quantity).append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    static String renderReorderCsv(List<ReorderSuggestion> reorder) {
        StringBuilder sb = new StringBuilder("gtin,name,onHand,par,suggestedOrderQty,estimatedCost\n");
        for (ReorderSuggestion s : reorder) {
            if (!s.needsReorder) continue;
            sb.append(csv(s.gtin)).append(',').append(csv(s.name)).append(',').append(s.onHand).append(',')
              .append(s.parProvided ? s.par : s.suggestedPar).append(',').append(s.suggestedOrderQty).append(',')
              .append(money(s.estimatedCost)).append('\n');
        }
        return sb.toString();
    }

    private static void kpi(StringBuilder sb, String label, String value) {
        sb.append("<span class='kpi'><b>").append(value).append("</b> ").append(label).append("</span>");
    }

    private static String money(double value) {
        return String.format("%.2f", value);
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String csv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `ManagementReportTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/ManagementReport.java medical-supply-java/src/test/java/org/medsupply/ManagementReportTest.java
git commit -m "feat(medsupply): management report (HTML + CSV)"
```

---

### Task 5: HTTP API server + minimal bundled UI

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/BrowserServer.java`
- Create: `medical-supply-java/src/main/resources/web/index.html`
- Create: `medical-supply-java/src/main/resources/web/app.js`
- Test: `medical-supply-java/src/test/java/org/medsupply/BrowserServerTest.java`

**Interfaces:**
- Consumes: `AppService` (Tasks 1–3), `ManagementReport` (Task 4), `Json` (Plan 1), `AppConfig`.
- Produces:
  - `new BrowserServer(AppService service, AppConfig config)`.
  - `String start()` — binds `127.0.0.1:0`, registers handlers + monitor, returns the origin (`http://127.0.0.1:<port>`); does not block, does not open a browser.
  - `void startAndOpen()` — calls `start()`, opens the system browser (unless `-Dmedsupply.noDesktop=true`), then blocks until `/api/shutdown`.
  - `void stop()`; `String token()` (package-visible, for tests).
  - Endpoints (all POST bodies are JSON objects of strings; all require `X-Session-Token`): `GET /api/state`, `POST /api/configure`, `/api/receive`, `/api/pick`, `/api/adjust`, `/api/archive`, `/api/register`, `/api/gudid`, `/api/report`, `/api/shutdown`; static `GET /` → `/web/index.html` (with `__SESSION_TOKEN__` substituted).

- [ ] **Step 1: Write the failing test (starts the server, calls the API)**

`medical-supply-java/src/test/java/org/medsupply/BrowserServerTest.java`:

```java
package org.medsupply;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class BrowserServerTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-http");
        System.setProperty("medsupply.localBase", base.toString());
        System.setProperty("medsupply.noDesktop", "true");
        AppConfig config = AppConfig.load();
        AppService svc = new AppService(config, null);
        BrowserServer server = new BrowserServer(svc, config);
        String origin = server.start();
        try {
            Map<String, Object> configure = post(origin, "/api/configure", server.token(),
                    "{\"sharedRoot\":" + Json.write(base.resolve("shared").toString()) + "}");
            check(configure.containsKey("message"), "configure ok");

            post(origin, "/api/register", server.token(),
                    "{\"gtin\":\"00380740000010\",\"name\":\"Stent\",\"manufacturer\":\"Abbott\","
                    + "\"category\":\"Coronary stent\",\"unitPrice\":\"10\",\"par\":\"4\",\"notes\":\"\",\"source\":\"MANUAL\"}");
            post(origin, "/api/receive", server.token(),
                    "{\"raw\":\"010038074000001017261130" + "10L1\",\"quantity\":\"5\",\"force\":\"false\"}");

            Map<String, Object> state = get(origin, "/api/state", server.token());
            check(Json.asList(state.get("stock")).size() == 1, "one stock line via api");

            // Missing token is rejected.
            int status = statusOf(origin, "/api/state", null);
            check(status == 400 || status == 403, "missing token rejected: " + status);
        } finally {
            server.stop();
        }
        System.out.println("BrowserServerTest: PASS");
    }

    private static Map<String, Object> get(String origin, String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + path).openConnection();
        c.setRequestMethod("GET");
        if (token != null) c.setRequestProperty("X-Session-Token", token);
        return Json.asMap(Json.parse(read(c)));
    }

    private static Map<String, Object> post(String origin, String path, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + path).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("X-Session-Token", token);
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        return Json.asMap(Json.parse(read(c)));
    }

    private static int statusOf(String origin, String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + path).openConnection();
        c.setRequestMethod("GET");
        if (token != null) c.setRequestProperty("X-Session-Token", token);
        return c.getResponseCode();
    }

    private static String read(HttpURLConnection c) throws Exception {
        java.io.InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... BrowserServer`.

- [ ] **Step 3: Write `BrowserServer`**

`medical-supply-java/src/main/java/org/medsupply/BrowserServer.java`:

```java
package org.medsupply;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class BrowserServer {
    private final AppService service;
    private final AppConfig config;
    private final String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private HttpServer server;
    private String origin;

    public BrowserServer(AppService service, AppConfig config) {
        this.service = service;
        this.config = config;
    }

    public String token() {
        return token;
    }

    public String start() throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "medsupply-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return origin;
    }

    public void startAndOpen() throws Exception {
        start();
        URI uri = URI.create(origin + "/");
        System.out.println("Medical Supply UI: " + uri);
        if (Boolean.getBoolean("medsupply.noDesktop")) {
            // Automated/smoke runs attach a browser explicitly.
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            throw new IOException("No supported system browser was found. Run with --classic-ui.");
        }
        stopped.await();
    }

    public void stop() {
        if (server != null) server.stop(0);
        server = null;
        stopped.countDown();
    }

    private Map<String, Object> route(String path, String method, Map<String, String> body) throws IOException {
        Instant now = Instant.now();
        if ("/api/state".equals(path) && "GET".equals(method)) {
            return service.snapshot(now);
        }
        if ("/api/configure".equals(path)) {
            service.configure(Paths.get(required(body, "sharedRoot")));
            return message("Shared folder configured.");
        }
        if ("/api/receive".equals(path)) {
            return service.receive(required(body, "raw"), intValue(body, "quantity", 1),
                    "true".equalsIgnoreCase(value(body, "force", "false")));
        }
        if ("/api/pick".equals(path)) {
            return service.pick(required(body, "gtin"), value(body, "lot", ""),
                    value(body, "expirationIso", ""), intValue(body, "quantity", 1));
        }
        if ("/api/adjust".equals(path)) {
            return service.adjust(required(body, "gtin"), value(body, "lot", ""),
                    value(body, "expirationIso", ""), intValue(body, "quantity", 0));
        }
        if ("/api/archive".equals(path)) {
            return service.archive(required(body, "gtin"), value(body, "lot", ""),
                    value(body, "expirationIso", ""), required(body, "reason"));
        }
        if ("/api/register".equals(path)) {
            return service.registerProduct(required(body, "gtin"), required(body, "name"),
                    value(body, "manufacturer", ""), value(body, "category", ""),
                    doubleValue(body, "unitPrice"), intValue(body, "par", -1),
                    value(body, "notes", ""), value(body, "source", "MANUAL"));
        }
        if ("/api/gudid".equals(path)) {
            return service.lookupGudid(required(body, "gtin"));
        }
        if ("/api/report".equals(path)) {
            if (!service.configured()) throw new AppService.BadRequest("Configure a folder first.");
            ManagementReport.Result result = ManagementReport.write(
                    service.store().getSharedRoot().resolve("reports"),
                    service.dashboard(now), service.reorder(now), service.stock(), now);
            Map<String, Object> response = message("Management report written.");
            response.put("htmlFile", result.html.getFileName().toString());
            response.put("csvFile", result.csv.getFileName().toString());
            return response;
        }
        if ("/api/shutdown".equals(path)) {
            new Thread(this::stop, "medsupply-shutdown").start();
            return message("Shutting down.");
        }
        return null;
    }

    private final class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            try {
                secure(exchange);
                Map<String, String> body = "GET".equals(exchange.getRequestMethod())
                        ? new LinkedHashMap<String, String>() : body(exchange);
                Map<String, Object> response = route(exchange.getRequestURI().getPath(),
                        exchange.getRequestMethod(), body);
                if (response == null) { send(exchange, 404, message("Not found.")); return; }
                send(exchange, 200, response);
            } catch (AppService.BadRequest ex) {
                send(exchange, 400, message(ex.getMessage()));
            } catch (Exception ex) {
                send(exchange, 500, message("Operation failed: " + ex.getMessage()));
            }
        }

        private void secure(HttpExchange exchange) {
            String supplied = exchange.getRequestHeaders().getFirst("X-Session-Token");
            if (!token.equals(supplied)) throw new AppService.BadRequest("Invalid local session token.");
            String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
            if (requestOrigin != null && !origin.equals(requestOrigin))
                throw new AppService.BadRequest("Invalid request origin.");
        }
    }

    private final class StaticHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";
            if (path.contains("..")) { exchange.sendResponseHeaders(404, -1); return; }
            byte[] bytes = resource("/web" + path);
            if (bytes == null) { exchange.sendResponseHeaders(404, -1); return; }
            if ("/index.html".equals(path)) {
                bytes = new String(bytes, StandardCharsets.UTF_8)
                        .replace("__SESSION_TOKEN__", token).getBytes(StandardCharsets.UTF_8);
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(path));
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static byte[] resource(String name) throws IOException {
        InputStream in = BrowserServer.class.getResourceAsStream(name);
        if (in == null) return null;
        try { return readBounded(in, 5 * 1024 * 1024); } finally { in.close(); }
    }

    private static Map<String, String> body(HttpExchange exchange) throws IOException {
        byte[] bytes = readBounded(exchange.getRequestBody(), 1024 * 1024);
        Map<String, Object> parsed = Json.asMap(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
        Map<String, String> flat = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) flat.put(entry.getKey(), Json.str(parsed, entry.getKey()));
        return flat;
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> value) throws IOException {
        byte[] bytes = Json.write(value).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static byte[] readBounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Input exceeds limit.");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        return "application/octet-stream";
    }

    private static Map<String, Object> message(String text) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("message", text);
        return response;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim().length() == 0) throw new AppService.BadRequest("Missing " + key + ".");
        return value.trim();
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value.trim();
    }

    private static int intValue(Map<String, String> values, String key, int fallback) {
        try { return Integer.parseInt(value(values, key, String.valueOf(fallback))); }
        catch (NumberFormatException ex) { throw new AppService.BadRequest("Invalid " + key + "."); }
    }

    private static double doubleValue(Map<String, String> values, String key) {
        try { return Double.parseDouble(value(values, key, "0")); }
        catch (NumberFormatException ex) { throw new AppService.BadRequest("Invalid " + key + "."); }
    }
}
```

- [ ] **Step 4: Write the minimal bundled UI**

`medical-supply-java/src/main/resources/web/index.html`:

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Medical Supply Tracking</title>
<style>
  body { font-family: Segoe UI, Arial, sans-serif; margin: 0; color: #1f2937; }
  header { background: #6264a7; color: #fff; padding: 12px 20px; font-size: 18px; font-weight: 600; }
  main { padding: 20px; }
  .tiles { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
  .tile { border: 1px solid #d1d5db; border-radius: 8px; padding: 12px 16px; min-width: 120px; }
  .tile b { font-size: 22px; display: block; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #d1d5db; padding: 6px 8px; font-size: 13px; text-align: left; }
  input, button { font-size: 14px; padding: 6px 8px; margin: 2px; }
  #msg { margin: 8px 0; font-weight: 600; }
</style>
</head>
<body>
<header>Medical Supply Tracking</header>
<main>
  <div id="msg"></div>
  <div>
    <input id="root" placeholder="Synchronized folder path" size="50">
    <button onclick="configure()">Set folder</button>
    <button onclick="report()">Export report</button>
  </div>
  <h3>Receive</h3>
  <input id="raw" placeholder="Scan barcode" size="40">
  <input id="qty" type="number" value="1" min="1" style="width:70px">
  <button onclick="receive()">Receive</button>
  <div id="dashboard" class="tiles"></div>
  <h3>Inventory</h3>
  <table id="stock"><thead><tr><th>Name</th><th>GTIN</th><th>Lot</th><th>Exp</th><th>Qty</th></tr></thead><tbody></tbody></table>
</main>
<script>window.SESSION_TOKEN = "__SESSION_TOKEN__";</script>
<script src="/app.js"></script>
</body>
</html>
```

`medical-supply-java/src/main/resources/web/app.js`:

```javascript
const TOKEN = window.SESSION_TOKEN;

async function api(path, method, body) {
  const res = await fetch(path, {
    method: method || "GET",
    headers: { "X-Session-Token": TOKEN, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || "Request failed");
  return data;
}

function msg(text, ok) {
  const el = document.getElementById("msg");
  el.textContent = text;
  el.style.color = ok === false ? "#b91c1c" : "#0b6a0b";
}

async function refresh() {
  try {
    const s = await api("/api/state");
    const d = s.dashboard || {};
    const tiles = [
      ["SKUs", d.distinctSkus], ["On-hand value", (d.onHandValue || 0).toFixed(2)],
      ["Expired", d.expired], ["Expiring 7d", d.expiring7], ["Expiring 30d", d.expiring30],
      ["Out of stock", d.outOfStock], ["Stale", d.stale]
    ];
    document.getElementById("dashboard").innerHTML =
      tiles.map(t => `<div class="tile"><b>${t[1] ?? 0}</b>${t[0]}</div>`).join("");
    const rows = (s.stock || []).map(l =>
      `<tr><td>${esc(l.name)}</td><td>${esc(l.gtin)}</td><td>${esc(l.lot)}</td><td>${esc(l.expirationIso)}</td><td>${l.quantity}</td></tr>`);
    document.querySelector("#stock tbody").innerHTML = rows.join("");
    if (s.sharedRoot) document.getElementById("root").value = s.sharedRoot;
  } catch (e) { msg(e.message, false); }
}

async function configure() {
  try { await api("/api/configure", "POST", { sharedRoot: document.getElementById("root").value }); msg("Folder set."); refresh(); }
  catch (e) { msg(e.message, false); }
}

async function receive() {
  const raw = document.getElementById("raw").value;
  const quantity = document.getElementById("qty").value;
  try {
    const r = await api("/api/receive", "POST", { raw, quantity, force: "false" });
    if (r.needsRegistration) {
      const name = prompt("Unknown product " + r.gtin + ". Product name to register:");
      if (!name) return;
      const sug = r.suggestion || {};
      await api("/api/register", "POST", { gtin: r.gtin, name, manufacturer: sug.manufacturer || "", category: sug.category || "", source: sug.found ? "GUDID" : "MANUAL" });
      await api("/api/receive", "POST", { raw, quantity, force: "true" });
    }
    msg("Received.");
    document.getElementById("raw").value = "";
    refresh();
  } catch (e) { msg(e.message, false); }
}

async function report() {
  try { const r = await api("/api/report", "POST", {}); msg("Report: " + r.htmlFile); }
  catch (e) { msg(e.message, false); }
}

function esc(v) { return (v || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }

refresh();
setInterval(refresh, 15000);
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `BrowserServerTest: PASS`. (The static resources are not needed for the test to pass, but they must exist for the app to serve a UI; Task 6 bundles them.)

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/BrowserServer.java medical-supply-java/src/main/resources/web/index.html medical-supply-java/src/main/resources/web/app.js medical-supply-java/src/test/java/org/medsupply/BrowserServerTest.java
git commit -m "feat(medsupply): loopback HTTP API server and minimal bundled UI"
```

---

### Task 6: Wire the launcher and bundle web resources

**Files:**
- Modify: `medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java`
- Modify: `medical-supply-java/build.ps1`

**Interfaces:**
- Consumes: `BrowserServer`, `AppService`, `AppConfig`, `GudidClient`/`HttpsFetcher` (Plans 2–3).
- Produces: `MedicalSupplyApp.main` launches the browser UI by default; `--self-test` unchanged; `--classic-ui` prints a "Plan 4" notice (Swing arrives in Plan 4). `build.ps1` copies `src/main/resources/**` into the compiled classes before packaging so `/web/**` is on the classpath.

- [ ] **Step 1: Update `build.ps1` to bundle resources**

In `medical-supply-java/build.ps1`, immediately after the main-compilation block (after the `if ($LASTEXITCODE -ne 0) { throw "Main compilation failed." }` line), insert:

```powershell
$resources = Join-Path $projectRoot "src\main\resources"
if (Test-Path $resources) {
    Copy-Item -Path (Join-Path $resources "*") -Destination $classes -Recurse -Force
}
```

- [ ] **Step 2: Update `MedicalSupplyApp.main` to launch the server**

Replace the body of `MedicalSupplyApp.main` in `medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java` with:

```java
    public static void main(String[] args) {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            try {
                SelfTest.run();
                System.out.println("MedicalSupply self-test: PASS");
            } catch (Exception ex) {
                System.err.println("MedicalSupply self-test: FAIL - " + ex.getMessage());
                ex.printStackTrace(System.err);
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && "--classic-ui".equals(args[0])) {
            System.out.println("The classic Swing UI arrives in Plan 4. Launch without arguments for the browser UI.");
            return;
        }
        try {
            AppConfig config = AppConfig.load();
            GudidClient gudid = config.gudidEnabled
                    ? new GudidClient(config.gudidEndpoint, new HttpsFetcher()) : null;
            new BrowserServer(new AppService(config, gudid), config).startAndOpen();
        } catch (Exception ex) {
            System.err.println("Medical Supply UI failed: " + ex.getMessage());
            System.exit(1);
        }
    }
```

- [ ] **Step 3: Build, run all tests, verify the JAR serves the UI**

Run:
```
powershell -File medical-supply-java/build.ps1
```
Expected: every `*Test: PASS` (including `AppServiceTest`, `AppServiceStockTest`, `AppServiceGudidTest`, `ManagementReportTest`, `BrowserServerTest`) and `Built: ...`.

Then verify the packaged server serves the UI headlessly:
```
java -Dmedsupply.noDesktop=true -cp medical-supply-java/dist/MedicalSupply-RC.jar org.medsupply.BrowserServer 2>NUL || echo "(server has no main; use the app)"
```
Prefer this manual check: launch the app, note the printed `Medical Supply UI: http://127.0.0.1:<port>`, open it in a browser, set a folder, and scan. (Automated coverage is `BrowserServerTest`.)

- [ ] **Step 4: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java medical-supply-java/build.ps1
git commit -m "feat(medsupply): launch browser UI by default and bundle web resources"
```

---

## Self-Review

**Spec coverage (Plan 3 scope):**
- Shared application service over the domain (enables §7 workspaces' data/actions) → Tasks 1–3. ✓
- Scan → receive/pick/adjust/archive, register (with unknown-GTIN → registration flow), GUDID prefill (§7 Scan/Rapid Scan/Inventory/Registration) → Tasks 2–3. ✓
- Dashboard metrics + reorder surfaced through `snapshot()` and the browser UI tiles (§6.1, §6.2) → Tasks 1, 5. ✓
- Exportable management report HTML + CSV (§6.3) → Task 4, exposed via `/api/report` (Task 5). ✓
- Loopback-only server, ephemeral port, session token, Origin guard, static UI from JAR (§7 UI hosting) → Task 5. ✓
- Launcher + resource bundling → Task 6. ✓
- **Deferred to Plan 4 (presentation layer):** React/MUI SPA replacing the minimal bundled UI; Swing `--classic-ui` fallback; QR/label sheet with locally-generated QR (§7 Labels); management-report **PDF** via a ported `PortablePdf`; the 15s `WatchService` background monitor (the browser UI already polls `/api/state` every 15s, so this is a refinement); README/TESTING/RELEASE_NOTES + `dist-review/qualification` browser-smoke evidence and the `build.ps1` npm/frontend step.

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"similar to Task N". Each code step is complete and compilable. The Task 2 test uses `registerProduct`, whose implementation is included in Task 2 Step 3 (noted inline). ✓

**Type consistency:** `AppService` method names/signatures (`configure`, `reload`, `stock`, `catalog`, `dashboard(Instant)`, `reorder(Instant)`, `snapshot(Instant)`, `identity`, `store`, `receive/pick/adjust/archive/registerProduct/lookupGudid`, `BadRequest`) are used identically by the tests and by `BrowserServer.route`. `ManagementReport.write/renderHtml/renderReorderCsv/Result{html,csv}` match the test and the `/api/report` call. `BrowserServer` `start()`/`token()`/`stop()` match `BrowserServerTest` and `MedicalSupplyApp`. `snapshot()` keys (`dashboard`, `stock`, `catalog`, `reorder`, `configured`, `sharedRoot`) match `app.js` and the tests. ✓

**Runtime note verified against Plan 1/2:** `AppService` uses `config.staleDays`, `reorderWindowDays/leadDays/safetyDays/coverageDays`, `gudidEnabled`, `gudidEndpoint`, `activeSessionId`, `deviceId`, `actor`, `sharedRoot`, `localRoot` — all present on `AppConfig` (Plan 1 Task 6). `GudidClient(endpoint, Fetcher)` + `HttpsFetcher` are Plan 2 Task 6. ✓

## Execution Handoff

Plan 3 complete and saved. This is plan 3 of a 4-plan sequence. **Plan 4 (presentation): React/MUI SPA, Swing fallback, QR label sheet, report PDF, and qualification packaging** will be written after this is approved/executed, since it builds on the tested `/api/*` contract defined here.

Two execution options for Plan 3:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, review between tasks.
2. **Inline Execution** — execute tasks in this session with checkpoints.
