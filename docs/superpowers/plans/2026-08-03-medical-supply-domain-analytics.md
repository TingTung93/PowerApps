# Medical Supply Tracking — Plan 2: Domain & Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the medical-supply domain logic on top of the Plan 1 storage foundation: concrete event types, a robust GS1 barcode parser, catalog + inventory projections, dashboard analytics, the PAR/consumption reorder advisor, and an offline-first FDA GUDID client — all headless and self-testable.

**Architecture:** Pure domain code over the existing event store. Events are built by `SupplyEvents` factories and keyed by `ItemKey`. `Gs1Parser` decodes GS1 barcodes into a `Gs1Scan`. `Projection` replays `EventStore.loadAll()` output into a catalog map and inventory stock lines. `InventoryAnalytics` and `ReorderAdvisor` compute dashboard metrics and reorder suggestions as pure functions (clock injected for testability). `GudidClient` looks up devices through an injectable `Fetcher` so tests run offline against a fixture. No UI — that is Plan 3.

**Tech Stack:** Java 8 (`javac --release 8`), Java SE only. GUDID transport uses `javax.net.ssl.HttpsURLConnection` (no third-party libraries). JSON via the Plan 1 `Json` class.

## Global Constraints

- Target Java 8 bytecode: compile with `javac --release 8`; use only Java SE 8 APIs. No third-party libraries.
- Package root: `org.medsupply`. Build/test via the existing `medical-supply-java/build.ps1` (it auto-discovers every `*Test` class — no build-script edits needed).
- **Formatting: conventional, readable, multi-line Java** — one statement per line, standard indentation, matching `commercial-tracking-java`. Do not minify onto dense single lines. (Plan 1's engine files were minified; keep new files readable and reformat any file you substantially touch.)
- Depends on Plan 1 types (already built and verified): `SupplyEvent` (`eventType`, `occurredUtc`, `recordedUtc`, `deviceId`, `sessionId`, `actor`, `Map<String,String> payload`, `String payload(String)`), `EventStore` (`append`, `LoadResult{List<SupplyEvent> events; List<String> errors;}`), `AppConfig` (`reorderWindowDays`, `reorderLeadDays`, `reorderSafetyDays`, `reorderCoverageDays`, `staleDays`, `gudidEnabled`, `gudidEndpoint`), `Json` (`parse`, `write`, `asMap`, `asList`, `str`).
- Tests are framework-free `*Test` classes with `public static void main(String[])` that throw `AssertionError` on failure and print `XxxTest: PASS` on success.
- Time is always injected (`Instant now` / `LocalDate today`) — never call `Instant.now()` inside analytics/reorder logic, so tests are deterministic.
- Quantities are integers; unit price and usage rates are doubles. All UTC timestamps are ISO-8601 strings; expirations normalize to `yyyy-MM-dd` (or `""` when unknown).

---

### Task 1: Event types, payload keys, and factories

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/ItemKey.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/SupplyEvents.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/SupplyEventsTest.java`

**Interfaces:**
- Consumes: `SupplyEvent` (Plan 1).
- Produces:
  - `ItemKey.of(String gtin, String lot, String expirationIso) -> String` — returns `gtin + "|" + lot + "|" + yyyymmdd`, where `expirationIso` (`yyyy-MM-dd` or `""`) is reduced to its digits (`""` stays `""`).
  - `SupplyEvents` — event-type constants (`PRODUCT_REGISTERED`, `PRODUCT_UPDATED`, `STOCK_RECEIVED`, `STOCK_PICKED`, `STOCK_ADJUSTED`, `STOCK_ARCHIVED`, `STOCK_VOIDED`) and payload-key constants (`K_GTIN`, `K_LOT`, `K_EXPIRATION`, `K_BARCODE`, `K_ITEM_KEY`, `K_QUANTITY`, `K_NAME`, `K_MANUFACTURER`, `K_CATEGORY`, `K_UNIT_PRICE`, `K_PAR`, `K_NOTES`, `K_SOURCE`, `K_REASON`).
  - `SupplyEvents.Identity` — holder `Identity(String deviceId, String actor, String sessionId)`.
  - Factories, each returning a `SupplyEvent` with envelope identity + `occurredUtc`/`recordedUtc` set to `nowIso`:
    - `productRegistered(Identity id, String nowIso, String gtin, String name, String manufacturer, String category, double unitPrice, int par, String notes, String source)`
    - `productUpdated(...)` (same params as `productRegistered`)
    - `stockReceived(Identity id, String nowIso, String gtin, String lot, String expirationIso, String barcode, int quantity)`
    - `stockPicked(Identity id, String nowIso, String gtin, String lot, String expirationIso, int quantity)`
    - `stockAdjusted(Identity id, String nowIso, String gtin, String lot, String expirationIso, int absoluteQuantity)`
    - `stockArchived(Identity id, String nowIso, String gtin, String lot, String expirationIso, String reason)`
    - `stockVoided(...)` (same params as `stockArchived`)
  - Category is stored as a single string; multiple categories are joined with `"; "` by the caller.
  - PAR sentinel: `par < 0` means "unset"; factories still write the value verbatim.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/SupplyEventsTest.java`:

```java
package org.medsupply;

public final class SupplyEventsTest {
    public static void main(String[] args) {
        itemKeyStripsDashes();
        stockReceivedCarriesKeyAndQuantity();
        productRegisteredCarriesCatalogFields();
        System.out.println("SupplyEventsTest: PASS");
    }

    private static void itemKeyStripsDashes() {
        check("00380740000010|LOT9|20261130".equals(
                ItemKey.of("00380740000010", "LOT9", "2026-11-30")), "full key");
        check("00380740000010||".equals(
                ItemKey.of("00380740000010", "", "")), "empty lot/exp");
    }

    private static void stockReceivedCarriesKeyAndQuantity() {
        SupplyEvents.Identity id = new SupplyEvents.Identity("WS-1", "DOM\\alice", "sess-1");
        SupplyEvent e = SupplyEvents.stockReceived(id, "2026-08-03T10:00:00Z",
                "00380740000010", "LOT9", "2026-11-30", "0100380740000010", 5);
        check(SupplyEvents.STOCK_RECEIVED.equals(e.eventType), "type");
        check("WS-1".equals(e.deviceId), "device");
        check("2026-08-03T10:00:00Z".equals(e.occurredUtc), "occurred");
        check("00380740000010|LOT9|20261130".equals(e.payload(SupplyEvents.K_ITEM_KEY)), "itemKey");
        check("5".equals(e.payload(SupplyEvents.K_QUANTITY)), "qty");
        check("LOT9".equals(e.payload(SupplyEvents.K_LOT)), "lot");
    }

    private static void productRegisteredCarriesCatalogFields() {
        SupplyEvents.Identity id = new SupplyEvents.Identity("WS-1", "a", "s");
        SupplyEvent e = SupplyEvents.productRegistered(id, "2026-08-03T10:00:00Z",
                "00380740000010", "Stent", "Abbott", "Coronary stent", 12.50, 4, "note", "GUDID");
        check(SupplyEvents.PRODUCT_REGISTERED.equals(e.eventType), "type");
        check("Stent".equals(e.payload(SupplyEvents.K_NAME)), "name");
        check("Abbott".equals(e.payload(SupplyEvents.K_MANUFACTURER)), "man");
        check("12.5".equals(e.payload(SupplyEvents.K_UNIT_PRICE)), "price");
        check("4".equals(e.payload(SupplyEvents.K_PAR)), "par");
        check("GUDID".equals(e.payload(SupplyEvents.K_SOURCE)), "source");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... ItemKey` / `SupplyEvents`.

- [ ] **Step 3: Write `ItemKey`**

`medical-supply-java/src/main/java/org/medsupply/ItemKey.java`:

```java
package org.medsupply;

public final class ItemKey {
    private ItemKey() {}

    public static String of(String gtin, String lot, String expirationIso) {
        String digits = expirationIso == null ? "" : expirationIso.replaceAll("[^0-9]", "");
        return safe(gtin) + "|" + safe(lot) + "|" + digits;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
```

- [ ] **Step 4: Write `SupplyEvents`**

`medical-supply-java/src/main/java/org/medsupply/SupplyEvents.java`:

```java
package org.medsupply;

public final class SupplyEvents {
    private SupplyEvents() {}

    public static final String PRODUCT_REGISTERED = "PRODUCT_REGISTERED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String STOCK_RECEIVED = "STOCK_RECEIVED";
    public static final String STOCK_PICKED = "STOCK_PICKED";
    public static final String STOCK_ADJUSTED = "STOCK_ADJUSTED";
    public static final String STOCK_ARCHIVED = "STOCK_ARCHIVED";
    public static final String STOCK_VOIDED = "STOCK_VOIDED";

    public static final String K_GTIN = "gtin";
    public static final String K_LOT = "lot";
    public static final String K_EXPIRATION = "expiration";
    public static final String K_BARCODE = "barcode";
    public static final String K_ITEM_KEY = "itemKey";
    public static final String K_QUANTITY = "quantity";
    public static final String K_NAME = "name";
    public static final String K_MANUFACTURER = "manufacturer";
    public static final String K_CATEGORY = "category";
    public static final String K_UNIT_PRICE = "unitPrice";
    public static final String K_PAR = "par";
    public static final String K_NOTES = "notes";
    public static final String K_SOURCE = "source";
    public static final String K_REASON = "reason";

    public static final class Identity {
        public final String deviceId;
        public final String actor;
        public final String sessionId;

        public Identity(String deviceId, String actor, String sessionId) {
            this.deviceId = deviceId;
            this.actor = actor;
            this.sessionId = sessionId;
        }
    }

    public static SupplyEvent productRegistered(Identity id, String nowIso, String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes, String source) {
        return product(PRODUCT_REGISTERED, id, nowIso, gtin, name, manufacturer, category, unitPrice, par, notes, source);
    }

    public static SupplyEvent productUpdated(Identity id, String nowIso, String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes, String source) {
        return product(PRODUCT_UPDATED, id, nowIso, gtin, name, manufacturer, category, unitPrice, par, notes, source);
    }

    private static SupplyEvent product(String type, Identity id, String nowIso, String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes, String source) {
        SupplyEvent e = base(type, id, nowIso);
        e.payload.put(K_GTIN, nz(gtin));
        e.payload.put(K_NAME, nz(name));
        e.payload.put(K_MANUFACTURER, nz(manufacturer));
        e.payload.put(K_CATEGORY, nz(category));
        e.payload.put(K_UNIT_PRICE, trimNumber(unitPrice));
        e.payload.put(K_PAR, Integer.toString(par));
        e.payload.put(K_NOTES, nz(notes));
        e.payload.put(K_SOURCE, nz(source));
        return e;
    }

    public static SupplyEvent stockReceived(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String barcode, int quantity) {
        SupplyEvent e = stock(STOCK_RECEIVED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_BARCODE, nz(barcode));
        e.payload.put(K_QUANTITY, Integer.toString(quantity));
        return e;
    }

    public static SupplyEvent stockPicked(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, int quantity) {
        SupplyEvent e = stock(STOCK_PICKED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_QUANTITY, Integer.toString(quantity));
        return e;
    }

    public static SupplyEvent stockAdjusted(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, int absoluteQuantity) {
        SupplyEvent e = stock(STOCK_ADJUSTED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_QUANTITY, Integer.toString(absoluteQuantity));
        return e;
    }

    public static SupplyEvent stockArchived(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String reason) {
        SupplyEvent e = stock(STOCK_ARCHIVED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_REASON, nz(reason));
        return e;
    }

    public static SupplyEvent stockVoided(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String reason) {
        SupplyEvent e = stock(STOCK_VOIDED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_REASON, nz(reason));
        return e;
    }

    private static SupplyEvent stock(String type, Identity id, String nowIso, String gtin, String lot,
            String expirationIso) {
        SupplyEvent e = base(type, id, nowIso);
        e.payload.put(K_GTIN, nz(gtin));
        e.payload.put(K_LOT, nz(lot));
        e.payload.put(K_EXPIRATION, nz(expirationIso));
        e.payload.put(K_ITEM_KEY, ItemKey.of(gtin, lot, expirationIso));
        return e;
    }

    private static SupplyEvent base(String type, Identity id, String nowIso) {
        SupplyEvent e = new SupplyEvent();
        e.eventType = type;
        e.deviceId = id.deviceId;
        e.actor = id.actor;
        e.sessionId = id.sessionId;
        e.occurredUtc = nowIso;
        e.recordedUtc = nowIso;
        return e;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    static String trimNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) return Long.toString((long) value);
        return Double.toString(value);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `SupplyEventsTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/ItemKey.java medical-supply-java/src/main/java/org/medsupply/SupplyEvents.java medical-supply-java/src/test/java/org/medsupply/SupplyEventsTest.java
git commit -m "feat(medsupply): domain event types, item key, and factories"
```

---

### Task 2: GS1 barcode parser

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/Gs1Scan.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/Gs1Parser.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/Gs1ParserTest.java`

**Interfaces:**
- Consumes: `ItemKey` (Task 1).
- Produces:
  - `Gs1Scan` — public final fields: `String raw, gtin, lot, serial, count, expirationRaw` (YYMMDD or `""`), `String expirationIso` (`yyyy-MM-dd` or `""`), `boolean success` (GTIN present and 14 digits), `boolean requiresConfirmation`, `String note`. Method `String itemKey()` = `ItemKey.of(gtin, lot, expirationIso)`.
  - `Gs1Parser.parse(String raw) -> Gs1Scan` — decodes AI 01/17/10/21/30, honoring the FNC1/GS separator (ASCII 29) and falling back to next-AI heuristic termination for variable-length fields when no GS is present.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/Gs1ParserTest.java`:

```java
package org.medsupply;

public final class Gs1ParserTest {
    private static final char GS = (char) 29;

    public static void main(String[] args) {
        parsesConcatenatedFixedThenVariable();
        parsesWithGsSeparator();
        parsesParenthesizedHumanReadable();
        lotContainingSeventeenNotMisparsed();
        endOfMonthDayZero();
        failsWithoutGtin();
        System.out.println("Gs1ParserTest: PASS");
    }

    // 01 GTIN(14) 17 exp(6=261130) 10 lot(AB17CD) — lot has "17" inside, no GS.
    private static void parsesConcatenatedFixedThenVariable() {
        Gs1Scan s = Gs1Parser.parse("010038074000001017261130" + "10AB17CD");
        check(s.success, "success");
        check("00380740000010".equals(s.gtin), "gtin: " + s.gtin);
        check("261130".equals(s.expirationRaw), "expRaw: " + s.expirationRaw);
        check("2026-11-30".equals(s.expirationIso), "expIso: " + s.expirationIso);
        check("AB17CD".equals(s.lot), "lot: " + s.lot);
    }

    private static void parsesWithGsSeparator() {
        String raw = "0100380740000010" + "10LOT" + GS + "17261130";
        Gs1Scan s = Gs1Parser.parse(raw);
        check("00380740000010".equals(s.gtin), "gtin gs");
        check("LOT".equals(s.lot), "lot gs: " + s.lot);
        check("2026-11-30".equals(s.expirationIso), "exp gs");
    }

    private static void parsesParenthesizedHumanReadable() {
        Gs1Scan s = Gs1Parser.parse("(01)00380740000010(17)261130(10)AB17CD");
        check("00380740000010".equals(s.gtin), "gtin paren");
        check("AB17CD".equals(s.lot), "lot paren: " + s.lot);
        check("2026-11-30".equals(s.expirationIso), "exp paren");
    }

    private static void lotContainingSeventeenNotMisparsed() {
        // Lot printed before expiry, terminated by GS. Lot value literally "1799".
        String raw = "0100380740000010" + "101799" + GS + "17270101";
        Gs1Scan s = Gs1Parser.parse(raw);
        check("1799".equals(s.lot), "lot literal 1799: " + s.lot);
        check("2027-01-01".equals(s.expirationIso), "exp after lot");
    }

    private static void endOfMonthDayZero() {
        // AI 17 day "00" means end of month; 260200 -> 2026-02-28.
        Gs1Scan s = Gs1Parser.parse("010038074000001017260200");
        check("2026-02-28".equals(s.expirationIso), "eom: " + s.expirationIso);
    }

    private static void failsWithoutGtin() {
        Gs1Scan s = Gs1Parser.parse("17261130");
        check(!s.success, "no gtin fails");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... Gs1Parser`.

- [ ] **Step 3: Write `Gs1Scan`**

`medical-supply-java/src/main/java/org/medsupply/Gs1Scan.java`:

```java
package org.medsupply;

public final class Gs1Scan {
    public String raw = "";
    public String gtin = "";
    public String lot = "";
    public String serial = "";
    public String count = "";
    public String expirationRaw = "";
    public String expirationIso = "";
    public boolean success;
    public boolean requiresConfirmation;
    public String note = "";

    public String itemKey() {
        return ItemKey.of(gtin, lot, expirationIso);
    }
}
```

- [ ] **Step 4: Write `Gs1Parser`**

`medical-supply-java/src/main/java/org/medsupply/Gs1Parser.java`:

```java
package org.medsupply;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Gs1Parser {
    private Gs1Parser() {}

    private static final char GS = (char) 29;
    // Application Identifiers that can begin a new field; used to terminate a
    // variable-length field when no GS separator is present.
    private static final Set<String> KNOWN_AIS =
            new HashSet<String>(Arrays.asList("01", "10", "11", "15", "17", "21", "30", "240", "91"));

    public static Gs1Scan parse(String raw) {
        Gs1Scan scan = new Gs1Scan();
        scan.raw = raw == null ? "" : raw;
        String s = scan.raw.replace("(", "").replace(")", "");
        int i = 0;
        boolean sawUnknown = false;
        while (i + 2 <= s.length()) {
            if (s.charAt(i) == GS) { i++; continue; }
            String ai = s.substring(i, i + 2);
            i += 2;
            if ("01".equals(ai)) {
                String v = fixed(s, i, 14);
                scan.gtin = v;
                i += v.length();
            } else if ("17".equals(ai)) {
                String v = fixed(s, i, 6);
                scan.expirationRaw = v;
                i += v.length();
            } else if ("11".equals(ai) || "15".equals(ai)) {
                i += fixed(s, i, 6).length(); // production / best-before date: skip
            } else if ("10".equals(ai)) {
                int[] span = variable(s, i);
                scan.lot = s.substring(i, span[0]);
                i = span[1];
            } else if ("21".equals(ai)) {
                int[] span = variable(s, i);
                scan.serial = s.substring(i, span[0]);
                i = span[1];
            } else if ("30".equals(ai)) {
                int[] span = variable(s, i);
                scan.count = s.substring(i, span[0]);
                i = span[1];
            } else {
                sawUnknown = true;
                break; // unknown AI: stop rather than mis-slice
            }
        }
        scan.expirationIso = toIso(scan.expirationRaw);
        scan.success = scan.gtin.length() == 14 && scan.gtin.matches("[0-9]{14}");
        if (!scan.success) {
            scan.note = scan.gtin.length() == 0 ? "No GTIN (AI 01) found." : "GTIN is not 14 digits.";
        } else if (sawUnknown || (scan.expirationRaw.length() > 0 && scan.expirationIso.length() == 0)) {
            scan.requiresConfirmation = true;
            scan.note = "Barcode partially recognized; please confirm values.";
        }
        return scan;
    }

    private static String fixed(String s, int start, int len) {
        int end = Math.min(s.length(), start + len);
        return s.substring(start, end);
    }

    // Returns {contentEnd, nextIndex}: content is s[start..contentEnd), and
    // nextIndex resumes after any GS separator that terminated the field.
    private static int[] variable(String s, int start) {
        int gs = s.indexOf(GS, start);
        if (gs >= 0) return new int[] {gs, gs + 1};
        // No GS: terminate at the next known AI boundary, else end of string.
        for (int p = start + 1; p + 2 <= s.length(); p++) {
            if (KNOWN_AIS.contains(s.substring(p, p + 2))) return new int[] {p, p};
            if (p + 3 <= s.length() && KNOWN_AIS.contains(s.substring(p, p + 3))) return new int[] {p, p};
        }
        return new int[] {s.length(), s.length()};
    }

    private static String toIso(String yymmdd) {
        if (yymmdd == null || yymmdd.length() != 6 || !yymmdd.matches("[0-9]{6}")) return "";
        int year = 2000 + Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));
        if (month < 1 || month > 12) return "";
        int lastDay = java.time.YearMonth.of(year, month).lengthOfMonth();
        if (day == 0) day = lastDay;        // GS1: DD=00 means end of month
        if (day < 1 || day > lastDay) return "";
        return String.format("%04d-%02d-%02d", year, month, day);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `Gs1ParserTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/Gs1Scan.java medical-supply-java/src/main/java/org/medsupply/Gs1Parser.java medical-supply-java/src/test/java/org/medsupply/Gs1ParserTest.java
git commit -m "feat(medsupply): GS1 barcode parser with FNC1 handling"
```

---

### Task 3: Catalog + inventory projections

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/CatalogProduct.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/StockLine.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/Projection.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/ProjectionTest.java`

**Interfaces:**
- Consumes: `SupplyEvent` (Plan 1), `SupplyEvents` (Task 1).
- Produces:
  - `CatalogProduct` — public fields `String gtin, name, manufacturer, category, notes, source; double unitPrice; int par;` and `boolean hasPar()` (`par >= 0`).
  - `StockLine` — public fields `String itemKey, gtin, lot, expirationIso, barcode; int quantity; boolean active; String lastEventUtc, lastDevice;` and catalog-enriched `String name, manufacturer, category; double unitPrice; int par;`.
  - `Projection.replay(List<SupplyEvent> events) -> Projection` (static). Instance getters: `Map<String,CatalogProduct> catalog()` (keyed by GTIN), `List<StockLine> stock()` (all item keys, enriched from catalog by GTIN; ordered by itemKey). Quantities: `STOCK_RECEIVED` adds, `STOCK_PICKED` subtracts, `STOCK_ADJUSTED` sets absolute; `STOCK_ARCHIVED`/`STOCK_VOIDED` set `active=false`. Latest `PRODUCT_*` per GTIN wins (by `occurredUtc`, then `recordedUtc`).

Assume events arrive already sorted by `EventStore.loadAll()` (occurredUtc, recordedUtc, deviceId, eventId); `replay` additionally sorts defensively.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/ProjectionTest.java`:

```java
package org.medsupply;

import java.util.ArrayList;
import java.util.List;

public final class ProjectionTest {
    private static final SupplyEvents.Identity ID = new SupplyEvents.Identity("WS-1", "a", "s");

    public static void main(String[] args) {
        replaysQuantitiesAndEnrichment();
        adjustThenArchive();
        System.out.println("ProjectionTest: PASS");
    }

    private static void replaysQuantitiesAndEnrichment() {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        events.add(SupplyEvents.productRegistered(ID, "2026-08-01T09:00:00Z",
                "00380740000010", "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "GUDID"));
        events.add(SupplyEvents.stockReceived(ID, "2026-08-02T09:00:00Z",
                "00380740000010", "L1", "2026-11-30", "bc1", 10));
        events.add(SupplyEvents.stockPicked(ID, "2026-08-03T09:00:00Z",
                "00380740000010", "L1", "2026-11-30", 3));

        Projection p = Projection.replay(events);
        check(p.catalog().size() == 1, "one product");
        CatalogProduct product = p.catalog().get("00380740000010");
        check("Stent".equals(product.name) && product.par == 4, "catalog fields");

        check(p.stock().size() == 1, "one stock line");
        StockLine line = p.stock().get(0);
        check(line.quantity == 7, "qty 10-3=7, got " + line.quantity);
        check(line.active, "active");
        check("Stent".equals(line.name), "enriched name");
        check("Abbott".equals(line.manufacturer), "enriched manufacturer");
        check(line.unitPrice == 10.0, "enriched price");
        check("2026-08-03T09:00:00Z".equals(line.lastEventUtc), "last event");
    }

    private static void adjustThenArchive() {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        events.add(SupplyEvents.stockReceived(ID, "2026-08-02T09:00:00Z", "G", "L", "", "bc", 5));
        events.add(SupplyEvents.stockAdjusted(ID, "2026-08-03T09:00:00Z", "G", "L", "", 2));
        events.add(SupplyEvents.stockArchived(ID, "2026-08-04T09:00:00Z", "G", "L", "", "expired"));

        Projection p = Projection.replay(events);
        StockLine line = p.stock().get(0);
        check(line.quantity == 2, "adjust sets absolute, got " + line.quantity);
        check(!line.active, "archived inactive");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... Projection`.

- [ ] **Step 3: Write `CatalogProduct` and `StockLine`**

`medical-supply-java/src/main/java/org/medsupply/CatalogProduct.java`:

```java
package org.medsupply;

public final class CatalogProduct {
    public String gtin = "";
    public String name = "";
    public String manufacturer = "";
    public String category = "";
    public String notes = "";
    public String source = "";
    public double unitPrice;
    public int par = -1;

    public boolean hasPar() {
        return par >= 0;
    }
}
```

`medical-supply-java/src/main/java/org/medsupply/StockLine.java`:

```java
package org.medsupply;

public final class StockLine {
    public String itemKey = "";
    public String gtin = "";
    public String lot = "";
    public String expirationIso = "";
    public String barcode = "";
    public int quantity;
    public boolean active = true;
    public String lastEventUtc = "";
    public String lastDevice = "";

    // Enriched from catalog by GTIN.
    public String name = "";
    public String manufacturer = "";
    public String category = "";
    public double unitPrice;
    public int par = -1;
}
```

- [ ] **Step 4: Write `Projection`**

`medical-supply-java/src/main/java/org/medsupply/Projection.java`:

```java
package org.medsupply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Projection {
    private final Map<String, CatalogProduct> catalog = new LinkedHashMap<String, CatalogProduct>();
    private final Map<String, StockLine> stock = new LinkedHashMap<String, StockLine>();

    private Projection() {}

    public static Projection replay(List<SupplyEvent> input) {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>(input);
        Collections.sort(events, Comparator.comparing((SupplyEvent e) -> e.occurredUtc)
                .thenComparing(e -> e.recordedUtc).thenComparing(e -> e.deviceId).thenComparing(e -> e.eventId));
        Projection p = new Projection();
        for (SupplyEvent e : events) p.apply(e);
        p.enrich();
        return p;
    }

    private void apply(SupplyEvent e) {
        String type = e.eventType;
        if (SupplyEvents.PRODUCT_REGISTERED.equals(type) || SupplyEvents.PRODUCT_UPDATED.equals(type)) {
            CatalogProduct product = new CatalogProduct();
            product.gtin = e.payload(SupplyEvents.K_GTIN);
            product.name = e.payload(SupplyEvents.K_NAME);
            product.manufacturer = e.payload(SupplyEvents.K_MANUFACTURER);
            product.category = e.payload(SupplyEvents.K_CATEGORY);
            product.notes = e.payload(SupplyEvents.K_NOTES);
            product.source = e.payload(SupplyEvents.K_SOURCE);
            product.unitPrice = parseDouble(e.payload(SupplyEvents.K_UNIT_PRICE));
            product.par = parseInt(e.payload(SupplyEvents.K_PAR), -1);
            if (product.gtin.length() > 0) catalog.put(product.gtin, product);
            return;
        }
        String key = e.payload(SupplyEvents.K_ITEM_KEY);
        if (key.length() == 0) return;
        StockLine line = stock.get(key);
        if (line == null) {
            line = new StockLine();
            line.itemKey = key;
            line.gtin = e.payload(SupplyEvents.K_GTIN);
            line.lot = e.payload(SupplyEvents.K_LOT);
            line.expirationIso = e.payload(SupplyEvents.K_EXPIRATION);
            stock.put(key, line);
        }
        if (e.payload(SupplyEvents.K_BARCODE).length() > 0) line.barcode = e.payload(SupplyEvents.K_BARCODE);
        line.lastEventUtc = e.occurredUtc;
        line.lastDevice = e.deviceId;
        int qty = parseInt(e.payload(SupplyEvents.K_QUANTITY), 0);
        if (SupplyEvents.STOCK_RECEIVED.equals(type)) {
            line.quantity += qty;
            line.active = true;
        } else if (SupplyEvents.STOCK_PICKED.equals(type)) {
            line.quantity -= qty;
        } else if (SupplyEvents.STOCK_ADJUSTED.equals(type)) {
            line.quantity = qty;
            line.active = true;
        } else if (SupplyEvents.STOCK_ARCHIVED.equals(type) || SupplyEvents.STOCK_VOIDED.equals(type)) {
            line.active = false;
        }
    }

    private void enrich() {
        for (StockLine line : stock.values()) {
            CatalogProduct product = catalog.get(line.gtin);
            if (product == null) continue;
            line.name = product.name;
            line.manufacturer = product.manufacturer;
            line.category = product.category;
            line.unitPrice = product.unitPrice;
            line.par = product.par;
        }
    }

    public Map<String, CatalogProduct> catalog() {
        return catalog;
    }

    public List<StockLine> stock() {
        List<StockLine> lines = new ArrayList<StockLine>(stock.values());
        Collections.sort(lines, Comparator.comparing((StockLine l) -> l.itemKey));
        return lines;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException ex) { return fallback; }
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value.trim()); } catch (NumberFormatException ex) { return 0.0; }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `ProjectionTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/CatalogProduct.java medical-supply-java/src/main/java/org/medsupply/StockLine.java medical-supply-java/src/main/java/org/medsupply/Projection.java medical-supply-java/src/test/java/org/medsupply/ProjectionTest.java
git commit -m "feat(medsupply): catalog and inventory projections"
```

---

### Task 4: Dashboard analytics

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/DashboardMetrics.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/InventoryAnalytics.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/InventoryAnalyticsTest.java`

**Interfaces:**
- Consumes: `StockLine` (Task 3), `SupplyEvent` (Plan 1).
- Produces:
  - `DashboardMetrics` — public fields: `int expired, expiring7, expiring30, outOfStock, stale, distinctSkus, totalUnits, activeEventsLast7; double onHandValue;`.
  - `InventoryAnalytics.compute(List<StockLine> stock, List<SupplyEvent> events, java.time.Instant now, int staleDays) -> DashboardMetrics`. Only `active` lines count toward stock metrics. `expired`: `expirationIso` non-empty and `< today`. `expiring7`/`expiring30`: within +7/+30 days inclusive and not already expired (a line counted in `expiring7` is also counted in `expiring30`). `outOfStock`: `quantity < 1`. `stale`: last event older than `staleDays`. `distinctSkus`: count of active lines with `quantity > 0`. `onHandValue`: `Σ quantity × unitPrice` over active lines with `quantity > 0`. `activeEventsLast7`: events whose `occurredUtc` is within the last 7 days.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/InventoryAnalyticsTest.java`:

```java
package org.medsupply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InventoryAnalyticsTest {
    public static void main(String[] args) {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(line("g1|a|20260801", 5, 2.0, "2026-08-01", true, "2026-08-02T00:00:00Z")); // expired
        stock.add(line("g2|b|20260806", 3, 4.0, "2026-08-06", true, "2026-08-02T00:00:00Z")); // expiring7
        stock.add(line("g3|c|20260828", 2, 1.0, "2026-08-28", true, "2026-08-02T00:00:00Z")); // expiring30
        stock.add(line("g4|d|20270101", 0, 9.0, "2027-01-01", true, "2026-01-01T00:00:00Z")); // out+stale
        StockLine archived = line("g5|e|", 100, 5.0, "", false, "2026-08-02T00:00:00Z");     // ignored
        stock.add(archived);

        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        SupplyEvents.Identity id = new SupplyEvents.Identity("d", "a", "s");
        events.add(SupplyEvents.stockReceived(id, "2026-08-02T00:00:00Z", "g2", "b", "2026-08-06", "bc", 3)); // recent
        events.add(SupplyEvents.stockReceived(id, "2026-06-01T00:00:00Z", "g4", "d", "2027-01-01", "bc", 1)); // old

        DashboardMetrics m = InventoryAnalytics.compute(stock, events, now, 30);
        check(m.expired == 1, "expired=" + m.expired);
        check(m.expiring7 == 1, "expiring7=" + m.expiring7);
        check(m.expiring30 == 2, "expiring30=" + m.expiring30);
        check(m.outOfStock == 1, "outOfStock=" + m.outOfStock);
        check(m.stale == 1, "stale=" + m.stale);
        check(m.distinctSkus == 3, "distinctSkus=" + m.distinctSkus);
        check(m.totalUnits == 10, "totalUnits=" + m.totalUnits);
        check(Math.abs(m.onHandValue - (5 * 2.0 + 3 * 4.0 + 2 * 1.0)) < 1e-9, "value=" + m.onHandValue);
        check(m.activeEventsLast7 == 1, "events7=" + m.activeEventsLast7);
        System.out.println("InventoryAnalyticsTest: PASS");
    }

    private static StockLine line(String key, int qty, double price, String exp, boolean active, String last) {
        StockLine l = new StockLine();
        l.itemKey = key;
        l.quantity = qty;
        l.unitPrice = price;
        l.expirationIso = exp;
        l.active = active;
        l.lastEventUtc = last;
        return l;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... InventoryAnalytics`.

- [ ] **Step 3: Write `DashboardMetrics`**

`medical-supply-java/src/main/java/org/medsupply/DashboardMetrics.java`:

```java
package org.medsupply;

public final class DashboardMetrics {
    public int expired;
    public int expiring7;
    public int expiring30;
    public int outOfStock;
    public int stale;
    public int distinctSkus;
    public int totalUnits;
    public int activeEventsLast7;
    public double onHandValue;
}
```

- [ ] **Step 4: Write `InventoryAnalytics`**

`medical-supply-java/src/main/java/org/medsupply/InventoryAnalytics.java`:

```java
package org.medsupply;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class InventoryAnalytics {
    private InventoryAnalytics() {}

    public static DashboardMetrics compute(List<StockLine> stock, List<SupplyEvent> events,
            Instant now, int staleDays) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Instant staleBefore = now.minusSeconds(staleDays * 86400L);
        Instant sevenAgo = now.minusSeconds(7 * 86400L);
        DashboardMetrics m = new DashboardMetrics();

        for (StockLine line : stock) {
            if (!line.active) continue;
            LocalDate exp = parseDate(line.expirationIso);
            if (exp != null) {
                if (exp.isBefore(today)) {
                    m.expired++;
                } else if (!exp.isAfter(today.plusDays(7))) {
                    m.expiring7++;
                    m.expiring30++;
                } else if (!exp.isAfter(today.plusDays(30))) {
                    m.expiring30++;
                }
            }
            if (line.quantity < 1) m.outOfStock++;
            if (isStale(line.lastEventUtc, staleBefore)) m.stale++;
            if (line.quantity > 0) {
                m.distinctSkus++;
                m.totalUnits += line.quantity;
                m.onHandValue += line.quantity * line.unitPrice;
            }
        }

        for (SupplyEvent e : events) {
            Instant occurred = parseInstant(e.occurredUtc);
            if (occurred != null && !occurred.isBefore(sevenAgo)) m.activeEventsLast7++;
        }
        return m;
    }

    private static boolean isStale(String lastEventUtc, Instant staleBefore) {
        Instant last = parseInstant(lastEventUtc);
        return last != null && last.isBefore(staleBefore);
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.length() == 0) return null;
        try { return LocalDate.parse(iso); } catch (RuntimeException ex) { return null; }
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.length() == 0) return null;
        try { return Instant.parse(iso); } catch (RuntimeException ex) { return null; }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `InventoryAnalyticsTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/DashboardMetrics.java medical-supply-java/src/main/java/org/medsupply/InventoryAnalytics.java medical-supply-java/src/test/java/org/medsupply/InventoryAnalyticsTest.java
git commit -m "feat(medsupply): dashboard analytics metrics"
```

---

### Task 5: PAR + consumption reorder advisor

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/ReorderSuggestion.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/ReorderAdvisor.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/ReorderAdvisorTest.java`

**Interfaces:**
- Consumes: `CatalogProduct`, `StockLine` (Task 3), `SupplyEvent`/`SupplyEvents` (Task 1).
- Produces:
  - `ReorderSuggestion` — public fields: `String gtin, name; int onHand; boolean parProvided; int par; double avgDailyUsage; int reorderPoint; int suggestedPar; int suggestedOrderQty; double estimatedCost; boolean needsReorder; boolean insufficientHistory;`.
  - `ReorderAdvisor.Params` — holder `Params(int windowDays, int leadDays, int safetyDays, int coverageDays)`.
  - `ReorderAdvisor.advise(Map<String,CatalogProduct> catalog, List<StockLine> stock, List<SupplyEvent> events, java.time.Instant now, Params params) -> List<ReorderSuggestion>` — one entry per catalog GTIN. On-hand = Σ active-line quantities for that GTIN. **PAR set** (`hasPar()`): `needsReorder = onHand < par`; `suggestedOrderQty = max(0, par − onHand)`; `parProvided = true`. **PAR unset:** compute `avgDailyUsage` = (units picked in the trailing `windowDays`) / `windowDays`. If no picks in window → `insufficientHistory = true`, `needsReorder = false`. Else `reorderPoint = ceil(avgDailyUsage × (leadDays + safetyDays))`, `suggestedPar = reorderPoint`, `needsReorder = onHand ≤ reorderPoint`, target `= ceil(avgDailyUsage × coverageDays)`, `suggestedOrderQty = max(0, target − onHand)`. `estimatedCost = suggestedOrderQty × unitPrice`. Sorted `needsReorder` first, then GTIN.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/ReorderAdvisorTest.java`:

```java
package org.medsupply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReorderAdvisorTest {
    private static final SupplyEvents.Identity ID = new SupplyEvents.Identity("d", "a", "s");
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    public static void main(String[] args) {
        parBelowTriggersReorder();
        consumptionDerivedWhenNoPar();
        insufficientHistoryWhenNoPicks();
        System.out.println("ReorderAdvisorTest: PASS");
    }

    private static void parBelowTriggersReorder() {
        Map<String, CatalogProduct> catalog = catalogOf("g1", "Gauze", 2.0, 10);
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(activeLine("g1", 4));
        List<ReorderSuggestion> out = ReorderAdvisor.advise(catalog, stock,
                new ArrayList<SupplyEvent>(), NOW, params());
        ReorderSuggestion s = out.get(0);
        check(s.parProvided, "par provided");
        check(s.onHand == 4, "onHand");
        check(s.needsReorder, "needs reorder");
        check(s.suggestedOrderQty == 6, "order 10-4=6, got " + s.suggestedOrderQty);
        check(Math.abs(s.estimatedCost - 12.0) < 1e-9, "cost");
    }

    private static void consumptionDerivedWhenNoPar() {
        Map<String, CatalogProduct> catalog = catalogOf("g2", "Glove", 1.0, -1);
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(activeLine("g2", 5));
        // 90 units picked across the 90-day window => 1/day.
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        events.add(SupplyEvents.stockPicked(ID, "2026-07-15T00:00:00Z", "g2", "L", "", 90));
        List<ReorderSuggestion> out = ReorderAdvisor.advise(catalog, stock, events, NOW, params());
        ReorderSuggestion s = out.get(0);
        check(!s.parProvided, "no par");
        check(!s.insufficientHistory, "has history");
        check(Math.abs(s.avgDailyUsage - 1.0) < 1e-9, "avg=" + s.avgDailyUsage);
        check(s.reorderPoint == 14, "rop=ceil(1*(7+7))=14, got " + s.reorderPoint);
        check(s.needsReorder, "5<=14 reorder");
        check(s.suggestedOrderQty == 23, "target ceil(1*28)=28 -5 =23, got " + s.suggestedOrderQty);
    }

    private static void insufficientHistoryWhenNoPicks() {
        Map<String, CatalogProduct> catalog = catalogOf("g3", "Tape", 1.0, -1);
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(activeLine("g3", 2));
        List<ReorderSuggestion> out = ReorderAdvisor.advise(catalog, stock,
                new ArrayList<SupplyEvent>(), NOW, params());
        ReorderSuggestion s = out.get(0);
        check(s.insufficientHistory, "insufficient");
        check(!s.needsReorder, "cannot advise");
    }

    private static ReorderAdvisor.Params params() {
        return new ReorderAdvisor.Params(90, 7, 7, 28);
    }

    private static Map<String, CatalogProduct> catalogOf(String gtin, String name, double price, int par) {
        CatalogProduct p = new CatalogProduct();
        p.gtin = gtin;
        p.name = name;
        p.unitPrice = price;
        p.par = par;
        Map<String, CatalogProduct> m = new LinkedHashMap<String, CatalogProduct>();
        m.put(gtin, p);
        return m;
    }

    private static StockLine activeLine(String gtin, int qty) {
        StockLine l = new StockLine();
        l.gtin = gtin;
        l.itemKey = gtin + "|L|";
        l.quantity = qty;
        l.active = true;
        return l;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... ReorderAdvisor`.

- [ ] **Step 3: Write `ReorderSuggestion`**

`medical-supply-java/src/main/java/org/medsupply/ReorderSuggestion.java`:

```java
package org.medsupply;

public final class ReorderSuggestion {
    public String gtin = "";
    public String name = "";
    public int onHand;
    public boolean parProvided;
    public int par = -1;
    public double avgDailyUsage;
    public int reorderPoint;
    public int suggestedPar;
    public int suggestedOrderQty;
    public double estimatedCost;
    public boolean needsReorder;
    public boolean insufficientHistory;
}
```

- [ ] **Step 4: Write `ReorderAdvisor`**

`medical-supply-java/src/main/java/org/medsupply/ReorderAdvisor.java`:

```java
package org.medsupply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReorderAdvisor {
    private ReorderAdvisor() {}

    public static final class Params {
        public final int windowDays;
        public final int leadDays;
        public final int safetyDays;
        public final int coverageDays;

        public Params(int windowDays, int leadDays, int safetyDays, int coverageDays) {
            this.windowDays = windowDays;
            this.leadDays = leadDays;
            this.safetyDays = safetyDays;
            this.coverageDays = coverageDays;
        }
    }

    public static List<ReorderSuggestion> advise(Map<String, CatalogProduct> catalog, List<StockLine> stock,
            List<SupplyEvent> events, Instant now, Params params) {
        Map<String, Integer> onHand = new HashMap<String, Integer>();
        for (StockLine line : stock) {
            if (!line.active) continue;
            Integer current = onHand.get(line.gtin);
            onHand.put(line.gtin, (current == null ? 0 : current) + line.quantity);
        }
        Map<String, Integer> pickedInWindow = pickedInWindow(events, now, params.windowDays);

        List<ReorderSuggestion> out = new ArrayList<ReorderSuggestion>();
        for (CatalogProduct product : catalog.values()) {
            ReorderSuggestion s = new ReorderSuggestion();
            s.gtin = product.gtin;
            s.name = product.name;
            s.onHand = onHand.containsKey(product.gtin) ? onHand.get(product.gtin) : 0;
            s.par = product.par;

            Integer picked = pickedInWindow.get(product.gtin);
            s.avgDailyUsage = picked == null ? 0.0 : (double) picked / params.windowDays;

            if (product.hasPar()) {
                s.parProvided = true;
                s.needsReorder = s.onHand < product.par;
                s.suggestedOrderQty = Math.max(0, product.par - s.onHand);
            } else if (picked == null || picked == 0) {
                s.insufficientHistory = true;
            } else {
                s.reorderPoint = (int) Math.ceil(s.avgDailyUsage * (params.leadDays + params.safetyDays));
                s.suggestedPar = s.reorderPoint;
                s.needsReorder = s.onHand <= s.reorderPoint;
                int target = (int) Math.ceil(s.avgDailyUsage * params.coverageDays);
                s.suggestedOrderQty = Math.max(0, target - s.onHand);
            }
            s.estimatedCost = s.suggestedOrderQty * product.unitPrice;
            out.add(s);
        }

        Collections.sort(out, new Comparator<ReorderSuggestion>() {
            public int compare(ReorderSuggestion a, ReorderSuggestion b) {
                if (a.needsReorder != b.needsReorder) return a.needsReorder ? -1 : 1;
                return a.gtin.compareTo(b.gtin);
            }
        });
        return out;
    }

    private static Map<String, Integer> pickedInWindow(List<SupplyEvent> events, Instant now, int windowDays) {
        Instant windowStart = now.minusSeconds(windowDays * 86400L);
        Map<String, Integer> picked = new HashMap<String, Integer>();
        for (SupplyEvent e : events) {
            if (!SupplyEvents.STOCK_PICKED.equals(e.eventType)) continue;
            Instant occurred;
            try { occurred = Instant.parse(e.occurredUtc); } catch (RuntimeException ex) { continue; }
            if (occurred.isBefore(windowStart) || occurred.isAfter(now)) continue;
            String gtin = e.payload(SupplyEvents.K_GTIN);
            int qty;
            try { qty = Integer.parseInt(e.payload(SupplyEvents.K_QUANTITY).trim()); }
            catch (NumberFormatException ex) { qty = 0; }
            Integer current = picked.get(gtin);
            picked.put(gtin, (current == null ? 0 : current) + qty);
        }
        return picked;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `ReorderAdvisorTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/ReorderSuggestion.java medical-supply-java/src/main/java/org/medsupply/ReorderAdvisor.java medical-supply-java/src/test/java/org/medsupply/ReorderAdvisorTest.java
git commit -m "feat(medsupply): PAR and consumption reorder advisor"
```

---

### Task 6: FDA GUDID client (offline-first, injectable fetch)

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/GudidResult.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/GudidClient.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/HttpsFetcher.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/GudidClientTest.java`

**Interfaces:**
- Consumes: `Json` (Plan 1).
- Produces:
  - `GudidResult` — public fields `boolean found; String gtin, brandName, companyName, deviceDescription, versionModelNumber, catalogNumber; java.util.List<String> gmdnTerms;`. Helpers: `String suggestedName()` (brandName else deviceDescription), `String suggestedCategory()` (gmdnTerms joined with `"; "`).
  - `GudidClient.Fetcher` — functional interface `String fetch(String url) throws java.io.IOException`.
  - `new GudidClient(String endpoint, Fetcher fetcher)`; `GudidResult lookup(String gtin)` — builds `endpoint + "?di=" + gtin`, fetches, parses. Robust to the device object being under `gudid.device`, `device`, or root; `gmdnTerms` being either an object with a `gmdn` array or a bare array. Returns `found = false` (never throws) on fetch/parse failure.
  - `HttpsFetcher` — a `Fetcher` using `HttpsURLConnection` (GET, 4s connect/read timeouts, `User-Agent` header). Non-200 → `IOException`. Not unit-tested (network); exercised manually.

- [ ] **Step 1: Write the failing test (offline fixture)**

`medical-supply-java/src/test/java/org/medsupply/GudidClientTest.java`:

```java
package org.medsupply;

public final class GudidClientTest {
    private static final String FIXTURE =
            "{\"gudid\":{\"device\":{"
            + "\"brandName\":\"XIENCE ALPINE\","
            + "\"companyName\":\"ABBOTT VASCULAR INC.\","
            + "\"deviceDescription\":\"Coronary stent system\","
            + "\"versionModelNumber\":\"1234\","
            + "\"catalogNumber\":\"CAT-9\","
            + "\"gmdnTerms\":{\"gmdn\":[{\"gmdnPTName\":\"Coronary artery stent, drug-eluting\"}]}"
            + "}}}";

    public static void main(String[] args) {
        parsesFixture();
        notFoundOnError();
        System.out.println("GudidClientTest: PASS");
    }

    private static void parsesFixture() {
        GudidClient client = new GudidClient("https://example/api", new GudidClient.Fetcher() {
            public String fetch(String url) {
                check(url.equals("https://example/api?di=00380740000010"), "url: " + url);
                return FIXTURE;
            }
        });
        GudidResult r = client.lookup("00380740000010");
        check(r.found, "found");
        check("XIENCE ALPINE".equals(r.brandName), "brand");
        check("ABBOTT VASCULAR INC.".equals(r.companyName), "company");
        check("XIENCE ALPINE".equals(r.suggestedName()), "suggested name");
        check(r.gmdnTerms.size() == 1, "one gmdn");
        check("Coronary artery stent, drug-eluting".equals(r.suggestedCategory()), "category");
    }

    private static void notFoundOnError() {
        GudidClient client = new GudidClient("https://example/api", new GudidClient.Fetcher() {
            public String fetch(String url) throws java.io.IOException {
                throw new java.io.IOException("offline");
            }
        });
        GudidResult r = client.lookup("00380740000010");
        check(!r.found, "offline -> not found, no throw");
        System.out.println("");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... GudidClient`.

- [ ] **Step 3: Write `GudidResult`**

`medical-supply-java/src/main/java/org/medsupply/GudidResult.java`:

```java
package org.medsupply;

import java.util.ArrayList;
import java.util.List;

public final class GudidResult {
    public boolean found;
    public String gtin = "";
    public String brandName = "";
    public String companyName = "";
    public String deviceDescription = "";
    public String versionModelNumber = "";
    public String catalogNumber = "";
    public List<String> gmdnTerms = new ArrayList<String>();

    public String suggestedName() {
        return brandName.length() > 0 ? brandName : deviceDescription;
    }

    public String suggestedCategory() {
        StringBuilder sb = new StringBuilder();
        for (String term : gmdnTerms) {
            if (term == null || term.length() == 0) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(term);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Write `GudidClient`**

`medical-supply-java/src/main/java/org/medsupply/GudidClient.java`:

```java
package org.medsupply;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class GudidClient {
    public interface Fetcher {
        String fetch(String url) throws IOException;
    }

    private final String endpoint;
    private final Fetcher fetcher;

    public GudidClient(String endpoint, Fetcher fetcher) {
        this.endpoint = endpoint;
        this.fetcher = fetcher;
    }

    public GudidResult lookup(String gtin) {
        GudidResult result = new GudidResult();
        result.gtin = gtin == null ? "" : gtin;
        try {
            String body = fetcher.fetch(endpoint + "?di=" + result.gtin);
            Map<String, Object> device = locateDevice(Json.asMap(Json.parse(body)));
            if (device.isEmpty()) return result;
            result.brandName = Json.str(device, "brandName");
            result.companyName = Json.str(device, "companyName");
            result.deviceDescription = Json.str(device, "deviceDescription");
            result.versionModelNumber = Json.str(device, "versionModelNumber");
            result.catalogNumber = Json.str(device, "catalogNumber");
            result.gmdnTerms = gmdnTerms(device.get("gmdnTerms"));
            result.found = result.brandName.length() > 0 || result.deviceDescription.length() > 0
                    || result.companyName.length() > 0;
        } catch (Exception ex) {
            result.found = false;
        }
        return result;
    }

    private static Map<String, Object> locateDevice(Map<String, Object> root) {
        Map<String, Object> gudid = Json.asMap(root.get("gudid"));
        Map<String, Object> device = Json.asMap(gudid.get("device"));
        if (!device.isEmpty()) return device;
        device = Json.asMap(root.get("device"));
        if (!device.isEmpty()) return device;
        return root;
    }

    private static java.util.List<String> gmdnTerms(Object node) {
        java.util.List<String> terms = new java.util.ArrayList<String>();
        List<Object> list;
        if (node instanceof Map) {
            list = Json.asList(Json.asMap(node).get("gmdn"));
        } else {
            list = Json.asList(node);
        }
        for (Object item : list) {
            String name = Json.str(Json.asMap(item), "gmdnPTName");
            if (name.length() > 0) terms.add(name);
        }
        return terms;
    }
}
```

- [ ] **Step 5: Write `HttpsFetcher` (real transport, not unit-tested)**

`medical-supply-java/src/main/java/org/medsupply/HttpsFetcher.java`:

```java
package org.medsupply;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public final class HttpsFetcher implements GudidClient.Fetcher {
    public String fetch(String url) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setRequestProperty("User-Agent", "MedicalSupply/0.1 (offline-first)");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("GUDID HTTP " + status);
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                int total = 0;
                while ((read = in.read(chunk)) >= 0) {
                    total += read;
                    if (total > 1024 * 1024) throw new IOException("GUDID response too large");
                    buffer.write(chunk, 0, read);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `GudidClientTest: PASS`.

- [ ] **Step 7: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/GudidResult.java medical-supply-java/src/main/java/org/medsupply/GudidClient.java medical-supply-java/src/main/java/org/medsupply/HttpsFetcher.java medical-supply-java/src/test/java/org/medsupply/GudidClientTest.java
git commit -m "feat(medsupply): offline-first FDA GUDID client"
```

---

### Task 7: Domain pipeline in the self-test

**Files:**
- Modify: `medical-supply-java/src/main/java/org/medsupply/SelfTest.java`

**Interfaces:**
- Consumes: `Gs1Parser`, `SupplyEvents`, `EventStore`, `Projection`, `InventoryAnalytics`, `ReorderAdvisor` (Tasks 1–5).
- Produces: an extended `SelfTest.run()` that drives a scan → events → store → projection → analytics → reorder pipeline against a temp folder, proving the domain layer end to end from the packaged JAR.

- [ ] **Step 1: Extend the self test**

Replace the body of `SelfTest.run()` in `medical-supply-java/src/main/java/org/medsupply/SelfTest.java` (keep the existing schema-version check and the existing store round-trip; append the following before the method returns):

```java
        // Domain pipeline smoke: parse a GS1 label, receive stock, project, analyze, advise.
        Gs1Scan parsed = Gs1Parser.parse("010038074000001017261130" + "10LOT1");
        if (!parsed.success) throw new AssertionError("GS1 parse failed");

        java.nio.file.Path shared2 = java.nio.file.Files.createTempDirectory("medsupply-selftest-domain");
        java.nio.file.Path local2 = java.nio.file.Files.createTempDirectory("medsupply-selftest-domain-local");
        EventStore store2 = new EventStore(shared2, local2);
        SupplyEvents.Identity id = new SupplyEvents.Identity("SELFTEST", "selftest", "sess");
        store2.append(SupplyEvents.productRegistered(id, "2026-08-01T00:00:00Z",
                parsed.gtin, "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "MANUAL"));
        store2.append(SupplyEvents.stockReceived(id, "2026-08-02T00:00:00Z",
                parsed.gtin, parsed.lot, parsed.expirationIso, parsed.raw, 2));

        EventStore.LoadResult loaded2 = store2.loadAll();
        if (!loaded2.errors.isEmpty()) throw new AssertionError("Domain load errors: " + loaded2.errors);
        Projection projection = Projection.replay(loaded2.events);
        if (projection.stock().size() != 1) throw new AssertionError("Expected 1 stock line");
        if (projection.stock().get(0).quantity != 2) throw new AssertionError("Expected qty 2");

        java.time.Instant now = java.time.Instant.parse("2026-08-03T00:00:00Z");
        DashboardMetrics metrics = InventoryAnalytics.compute(projection.stock(), loaded2.events, now, 30);
        if (metrics.distinctSkus != 1) throw new AssertionError("Expected 1 SKU");

        java.util.List<ReorderSuggestion> suggestions = ReorderAdvisor.advise(projection.catalog(),
                projection.stock(), loaded2.events, now, new ReorderAdvisor.Params(90, 7, 7, 28));
        if (suggestions.isEmpty() || !suggestions.get(0).needsReorder)
            throw new AssertionError("Expected a reorder suggestion (onHand 2 < par 4)");
```

- [ ] **Step 2: Build, run all tests, and run the packaged self-test**

Run:
```
powershell -File medical-supply-java/build.ps1
java -jar medical-supply-java/dist/MedicalSupply-RC.jar --self-test
```
Expected: every `*Test: PASS` line during the build (including the new `SupplyEventsTest`, `Gs1ParserTest`, `ProjectionTest`, `InventoryAnalyticsTest`, `ReorderAdvisorTest`, `GudidClientTest`), then `MedicalSupply self-test: PASS`.

- [ ] **Step 3: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/SelfTest.java
git commit -m "test(medsupply): end-to-end domain pipeline self-test"
```

---

## Self-Review

**Spec coverage (Plan 2 scope):**
- Event types & payloads (§3.3) → Task 1. ✓
- Item identity `gtin|lot|expiration(yyyymmdd)` (§3.4) → Task 1 (`ItemKey`). ✓
- GS1 parser AI 01/17/10 (+21/30), FNC1/GS handling, next-AI fallback, DD=00 end-of-month, confirmation flag (§4) → Task 2. ✓
- Catalog + inventory projections, replay semantics (§3.5) → Task 3. ✓
- Dashboard issue metrics: expired, expiring7/30, out-of-stock, stale, distinct SKUs, total units, on-hand value, recent activity (§6.1) → Task 4. ✓
- PAR + consumption reorder heuristic incl. insufficient-history, suggested PAR/order qty, est. cost (§6.2) → Task 5. ✓
- Offline-first GUDID client with brandName/companyName/gmdnTerms mapping, best-effort no-throw, real HTTPS transport (§5) → Task 6. ✓
- `--self-test` exercising the domain pipeline (§8) → Task 7. ✓
- Deferred to Plan 3: dashboard **UI**, exportable management **report** (§6.3), all workspaces, `BrowserServer`/SPA/Swing, QR labels, README/qualification packaging, `build.ps1` npm/frontend step, wiring `HttpsFetcher` + `AppConfig.gudidEnabled/gudidEndpoint` into the running app.

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"similar to Task N". Every code step is complete and compilable. ✓

**Type consistency:** `SupplyEvents.Identity(deviceId, actor, sessionId)` and all factory signatures are used identically in Tasks 1, 3, 4, 5, 7. Payload-key constants (`K_ITEM_KEY`, `K_QUANTITY`, `K_GTIN`, `K_PAR`, …) are referenced consistently by `Projection` (Task 3), `ReorderAdvisor` (Task 5). `Gs1Scan` fields (`success`, `gtin`, `lot`, `expirationIso`, `raw`) match `Gs1Parser` output and Task 7 usage. `CatalogProduct.hasPar()`/`par` and `StockLine.active`/`quantity`/`gtin`/`unitPrice` match across Tasks 3–5. `ReorderAdvisor.Params(windowDays, leadDays, safetyDays, coverageDays)` and `advise(...)` signature match the test and Task 7. `GudidClient.Fetcher.fetch(String)` and `GudidResult.suggestedName()/suggestedCategory()` match the test. ✓

**Reorder arithmetic re-checked against the test:** avg = 90/90 = 1.0/day; reorderPoint = ceil(1×(7+7)) = 14; onHand 5 ≤ 14 → needsReorder; target = ceil(1×28) = 28; order = 28−5 = 23. Matches `ReorderAdvisorTest`. ✓

## Execution Handoff

Plan 2 complete and saved. This is plan 2 of 3; Plan 3 (UI, labels, management report, and wiring the GUDID transport + config into the running app) will be written after this is approved/executed, since Plan 3 consumes these domain types.

Two execution options for Plan 2:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, review between tasks.
2. **Inline Execution** — execute tasks in this session with checkpoints.
