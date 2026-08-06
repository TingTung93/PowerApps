# Settings & Time Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement spec §0 (capture the package received date) and §6 (settings & time display) for `apps/commercial-tracking-java`: capture a never-overwritten `receivedUtc` in the projection, expose a host-zone `receivedDate` on the package maps, add a shared `timeFormat` (`12h`/`24h`) setting while retiring the `operationalTimeZone` requirement, provide deterministic date/time formatting helpers on both the Java and JS sides, and replace the pipe-delimited locations text field with a chip editor plus a 12/24-hour toggle in the settings UI.

**Architecture:** One new pure Java util class (`TimeFormat`) and one new additive projection field (`PackageState.receivedUtc`) feed a `receivedDate` string into the existing `BrowserServer` package maps. `SharedConfigManager` swaps its `operationalTimeZone` gate for a `timeFormat` gate (accepting but ignoring any legacy zone), and `saveSharedSettings` persists `timeFormat`. On the frontend, `format.js` gains a mutable 12/24-hour preference configured at state-load time, a new pure `locations.js` module powers a MUI chip editor, and `SettingsWorkspace` drops the operational-time-zone field. Backend logic is covered by `main()`-style test classes wired into `build.ps1`; frontend pure logic is covered by Node test scripts run through `npm test`; the React UI is verified via `npm run build` plus manual check.

**Tech Stack:** Java 8 (`javac --release 8`), pure JDK (`java.time`). No Maven/Gradle, no external jars. Backend tests are plain `main()` classes run by `build.ps1`. Frontend is React 19 + MUI 7 built by Vite; frontend tests are plain Node scripts (`node test/*.js`) chained through the `test` npm script.

## Global Constraints

- Pure JDK only — no third-party libraries may be added. Build is `javac --release 8 -encoding UTF-8`.
- New backend test classes are `public final class XxxTest { public static void main(String[] args) throws Exception { ... System.out.println("XxxTest: PASS"); } }` and must be added to `build.ps1` in the test-run block.
- Package is `org.commercialtracking`; source under `apps/commercial-tracking-java/src/main/java/org/commercialtracking/`, tests under `apps/commercial-tracking-java/src/test/java/org/commercialtracking/`.
- Backend build/test command (run from `apps/commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`.
- Frontend test/build commands (run from `apps/commercial-tracking-java/frontend/`): `npm test` and `npm run build`.
- All paths below are relative to the repo root `F:\PowerApps`.
- **Scope guard:** Do NOT modify `BrowserServer.manifest()` (~line 471/536), `BrowserServer.report()` (~line 580), `ManifestWorkspace` (~line 506), or `ReportsWorkspace` (~line 536 in `main.jsx`). Those `operationalTimeZone` reads belong to Plans 2 and 3. Do NOT change the persisted `locations` pipe-delimited format or the event-log schema (the `receivedUtc` projection field is additive and derived, not persisted to events).

## Shared-interface contract (other plans depend on these EXACT names)

- `org.commercialtracking.TimeFormat` — `static String date(String instantIso, java.time.ZoneId zone)`, `static String prepared(String instantIso, java.time.ZoneId zone, String timeFormat)`, `static String utcMinute(String instantIso)`.
- `PackageState.receivedUtc` — new `String` field, default `""`, carried in `copy()`.
- Package maps `sessionPackageMaps` / `packageMaps` each add `receivedUtc` and `receivedDate`.
- Shared setting `timeFormat` ∈ {`12h` (default), `24h`}; `operationalTimeZone` no longer required or validated.
- Frontend `format.js` — `configureTimeFormat(pref)` export plus the existing single `formatDate` entry point.
- Frontend `locations.js` — `parseLocations(pipeString) -> string[]`, `serializeLocations(array) -> pipeString`, `addLocation(array, candidate) -> {ok, list, error}`.

---

### Task 1: `PackageState.receivedUtc` + `Projection` sets it on first receive

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/PackageState.java`
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/Projection.java`
- Create: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/ProjectionTest.java`
- Modify: `apps/commercial-tracking-java/build.ps1`

**Interfaces:**
- Produces: `PackageState.receivedUtc` (`String`, default `""`, copied in `copy()`); `Projection.apply` sets `receivedUtc` on the first `PACKAGE_RECEIVED` for a package only when empty (never overwritten).
- Consumes: `TrackingEvent` (`eventType`, `trackingNumber`, `occurredUtc`, `deviceId`, `eventId`, `location`).

- [ ] **Step 1: Write the failing test**

Create `apps/commercial-tracking-java/src/test/java/org/commercialtracking/ProjectionTest.java`:

```java
package org.commercialtracking;

import java.util.ArrayList;
import java.util.List;

public final class ProjectionTest {
    public static void main(String[] args) {
        List<TrackingEvent> events = new ArrayList<TrackingEvent>();
        events.add(received("1Z999AA10123456784", "2026-08-04T14:00:00Z"));
        events.add(locationChanged("1Z999AA10123456784", "2026-08-04T15:30:00Z", "Loading Dock"));

        Projection projection = new Projection();
        projection.replay(events);
        PackageState state = projection.find("1Z999AA10123456784");
        check(state != null, "package present after receive");
        check("2026-08-04T14:00:00Z".equals(state.receivedUtc),
                "receivedUtc from first receive = " + state.receivedUtc);

        // A later receive for the same package must NOT overwrite the original received timestamp.
        events.add(received("1Z999AA10123456784", "2026-08-05T09:00:00Z"));
        projection.replay(events);
        state = projection.find("1Z999AA10123456784");
        check("2026-08-04T14:00:00Z".equals(state.receivedUtc),
                "receivedUtc unchanged by later receive = " + state.receivedUtc);

        // A package that was never received has an empty receivedUtc.
        List<TrackingEvent> locationOnly = new ArrayList<TrackingEvent>();
        locationOnly.add(locationChanged("1Z000AA10000000000", "2026-08-04T10:00:00Z", "Mailroom"));
        projection.replay(locationOnly);
        PackageState orphan = projection.find("1Z000AA10000000000");
        check(orphan != null && "".equals(orphan.receivedUtc),
                "receivedUtc empty without a receive event");

        System.out.println("ProjectionTest: PASS");
    }

    private static TrackingEvent received(String tracking, String occurredUtc) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_RECEIVED";
        event.trackingNumber = tracking;
        event.occurredUtc = occurredUtc;
        event.deviceId = "TEST-01";
        event.eventId = "evt-" + occurredUtc;
        return event;
    }

    private static TrackingEvent locationChanged(String tracking, String occurredUtc, String location) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_LOCATION_CHANGED";
        event.trackingNumber = tracking;
        event.occurredUtc = occurredUtc;
        event.location = location;
        event.deviceId = "TEST-01";
        event.eventId = "evt-loc-" + occurredUtc;
        return event;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Add the `ProjectionTest` run line to `build.ps1` first (Step 4 shows exactly where), then run from `apps/commercial-tracking-java/`:
`powershell -File build.ps1 -SkipFrontend`
Expected: compilation of the test fails — `PackageState` has no `receivedUtc` field.

- [ ] **Step 3: Write minimal implementation**

In `apps/commercial-tracking-java/src/main/java/org/commercialtracking/PackageState.java`, add the field after `manifestId` and carry it in `copy()`.

Add the field declaration (after line 13 `public String manifestId = "";`):

```java
    public String receivedUtc = "";
```

Change the `copy()` method so it also copies the new field. Replace:

```java
        p.manifestId = manifestId;
        return p;
```

with:

```java
        p.manifestId = manifestId;
        p.receivedUtc = receivedUtc;
        return p;
```

In `apps/commercial-tracking-java/src/main/java/org/commercialtracking/Projection.java`, set `receivedUtc` on the first receive. Replace the `PACKAGE_RECEIVED` branch:

```java
        if ("PACKAGE_RECEIVED".equals(event.eventType)) {
            if ("READY_FOR_PICKUP".equals(state.status)) {
                conflicts.add("Duplicate active receive: " + event.trackingNumber + " (" + state.lastDevice
                        + " and " + event.deviceId + ")");
            }
            state.status = "READY_FOR_PICKUP";
        } else if ("PACKAGE_LOCATION_CHANGED".equals(event.eventType)) {
```

with:

```java
        if ("PACKAGE_RECEIVED".equals(event.eventType)) {
            if ("READY_FOR_PICKUP".equals(state.status)) {
                conflicts.add("Duplicate active receive: " + event.trackingNumber + " (" + state.lastDevice
                        + " and " + event.deviceId + ")");
            }
            if (state.receivedUtc.length() == 0) state.receivedUtc = event.occurredUtc;
            state.status = "READY_FOR_PICKUP";
        } else if ("PACKAGE_LOCATION_CHANGED".equals(event.eventType)) {
```

- [ ] **Step 4: Wire the test into build.ps1**

In `apps/commercial-tracking-java/build.ps1`, add the `ProjectionTest` run line immediately after the `SharedConfigManagerTest` block. Replace:

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.SharedConfigManagerTest
    if ($LASTEXITCODE -ne 0) { throw "Shared settings tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.AddressBookStoreTest
```

with:

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.SharedConfigManagerTest
    if ($LASTEXITCODE -ne 0) { throw "Shared settings tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.ProjectionTest
    if ($LASTEXITCODE -ne 0) { throw "Projection tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.AddressBookStoreTest
```

- [ ] **Step 5: Run to verify it passes**

Run from `apps/commercial-tracking-java/`: `powershell -File build.ps1 -SkipFrontend`
Expected: output includes `ProjectionTest: PASS`; build succeeds.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/PackageState.java \
        apps/commercial-tracking-java/src/main/java/org/commercialtracking/Projection.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/ProjectionTest.java \
        apps/commercial-tracking-java/build.ps1
git commit -m "feat(projection): capture never-overwritten receivedUtc on first receive"
```

---

### Task 2: `TimeFormat` util + `TimeFormatTest`

**Files:**
- Create: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/TimeFormat.java`
- Create: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/TimeFormatTest.java`
- Modify: `apps/commercial-tracking-java/build.ps1`

**Interfaces:**
- Produces:
  - `static String date(String instantIso, java.time.ZoneId zone)` → `yyyy-MM-dd` in `zone`; `""` for null/empty input.
  - `static String prepared(String instantIso, java.time.ZoneId zone, String timeFormat)` → legible, NO seconds; `24h` → `MMM d, uuuu HH:mm`, otherwise (`12h`/`null`/default) → `MMM d, uuuu h:mm a`; `""` for null/empty input.
  - `static String utcMinute(String instantIso)` → `yyyy-MM-dd HH:mm 'UTC'` (minute precision, UTC); `""` for null/empty input.
- Consumes: `java.time.Instant.parse`, `ZonedDateTime`, `DateTimeFormatter` (`Locale.US`). Callers pass `java.time.ZoneId.systemDefault()` for host-local output; tests pass a fixed zone.

- [ ] **Step 1: Write the failing test**

Create `apps/commercial-tracking-java/src/test/java/org/commercialtracking/TimeFormatTest.java`:

```java
package org.commercialtracking;

import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TimeFormatTest {
    public static void main(String[] args) {
        // 2026-08-04 21:30:45 UTC. New York is UTC-4 in August -> 17:30 local (5:30 PM).
        String iso = "2026-08-04T21:30:45Z";
        ZoneId ny = ZoneId.of("America/New_York");

        check("2026-08-04".equals(TimeFormat.date(iso, ny)), "date ny = " + TimeFormat.date(iso, ny));
        check("2026-08-04".equals(TimeFormat.date(iso, ZoneOffset.UTC)), "date utc = " + TimeFormat.date(iso, ZoneOffset.UTC));
        check("".equals(TimeFormat.date("", ny)), "date empty input -> empty");
        check("".equals(TimeFormat.date(null, ny)), "date null input -> empty");

        String p12 = TimeFormat.prepared(iso, ny, "12h");
        check("Aug 4, 2026 5:30 PM".equals(p12), "prepared 12h = " + p12);
        String p24 = TimeFormat.prepared(iso, ny, "24h");
        check("Aug 4, 2026 17:30".equals(p24), "prepared 24h = " + p24);
        check(TimeFormat.prepared(iso, ny, null).equals(p12), "prepared null format defaults to 12h");
        check(TimeFormat.prepared(iso, ny, "anything").equals(p12), "prepared unknown format defaults to 12h");
        check(!p12.contains(":45") && p12.indexOf(':') == p12.lastIndexOf(':'), "12h has no seconds");
        check(!p24.contains(":45") && p24.indexOf(':') == p24.lastIndexOf(':'), "24h has no seconds");
        check("".equals(TimeFormat.prepared("", ny, "12h")), "prepared empty input -> empty");

        String utc = TimeFormat.utcMinute(iso);
        check("2026-08-04 21:30 UTC".equals(utc), "utcMinute = " + utc);
        check("".equals(TimeFormat.utcMinute("")), "utcMinute empty input -> empty");

        System.out.println("TimeFormatTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Add the `TimeFormatTest` run line to `build.ps1` first (Step 4), then run from `apps/commercial-tracking-java/`:
`powershell -File build.ps1 -SkipFrontend`
Expected: compilation fails — `TimeFormat` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `apps/commercial-tracking-java/src/main/java/org/commercialtracking/TimeFormat.java`:

```java
package org.commercialtracking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Deterministic, host-zone-aware date/time formatting. No seconds in human-facing times. */
public final class TimeFormat {
    private TimeFormat() { }

    private static final DateTimeFormatter PREPARED_24 = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm", Locale.US);
    private static final DateTimeFormatter PREPARED_12 = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a", Locale.US);
    private static final DateTimeFormatter UTC_MINUTE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'", Locale.US);

    /** yyyy-MM-dd in the supplied zone; "" when the instant is null/empty. */
    public static String date(String instantIso, ZoneId zone) {
        if (instantIso == null || instantIso.length() == 0) return "";
        return Instant.parse(instantIso).atZone(zone).toLocalDate().toString();
    }

    /** Legible date-time with NO seconds; 24h uses HH:mm, otherwise 12h with AM/PM. "" when null/empty. */
    public static String prepared(String instantIso, ZoneId zone, String timeFormat) {
        if (instantIso == null || instantIso.length() == 0) return "";
        ZonedDateTime zoned = Instant.parse(instantIso).atZone(zone);
        DateTimeFormatter formatter = "24h".equals(timeFormat) ? PREPARED_24 : PREPARED_12;
        return formatter.format(zoned);
    }

    /** yyyy-MM-dd HH:mm 'UTC' at minute precision. "" when null/empty. */
    public static String utcMinute(String instantIso) {
        if (instantIso == null || instantIso.length() == 0) return "";
        return Instant.parse(instantIso).atZone(ZoneOffset.UTC).format(UTC_MINUTE);
    }
}
```

Note: `LocalDate.toString()` renders as `yyyy-MM-dd` (ISO-8601), satisfying the `date` contract shape without a separate formatter.

- [ ] **Step 4: Wire the test into build.ps1**

In `apps/commercial-tracking-java/build.ps1`, add the `TimeFormatTest` run line immediately after the `ProjectionTest` block added in Task 1. Replace:

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.ProjectionTest
    if ($LASTEXITCODE -ne 0) { throw "Projection tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.AddressBookStoreTest
```

with:

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.ProjectionTest
    if ($LASTEXITCODE -ne 0) { throw "Projection tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.TimeFormatTest
    if ($LASTEXITCODE -ne 0) { throw "Time format tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.AddressBookStoreTest
```

- [ ] **Step 5: Run to verify it passes**

Run from `apps/commercial-tracking-java/`: `powershell -File build.ps1 -SkipFrontend`
Expected: output includes `TimeFormatTest: PASS`; build succeeds.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/TimeFormat.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/TimeFormatTest.java \
        apps/commercial-tracking-java/build.ps1
git commit -m "feat(time): add deterministic host-zone TimeFormat util (date/prepared/utcMinute)"
```

---

### Task 3: `BrowserServer` package maps expose `receivedUtc` + `receivedDate`

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java`

**Interfaces:**
- Consumes: `PackageState.receivedUtc` (Task 1), `TimeFormat.date` (Task 2).
- Produces: each row from `sessionPackageMaps()` and `packageMaps()` gains `receivedUtc` (`= state.receivedUtc`) and `receivedDate` (`= TimeFormat.date(state.receivedUtc, ZoneId.systemDefault())`, which is `""` when `receivedUtc` is empty). `java.time.ZoneId` is already imported in `BrowserServer.java`.

There is no unit harness for these private maps; correctness is verified by compilation + the full Java test suite passing, plus a manual/integration check noted below.

- [ ] **Step 1: Add fields to `sessionPackageMaps()`**

In `apps/commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java`, in `sessionPackageMaps()`, replace:

```java
            row.put("lastEventId", state.lastEventId);
            row.put("manifestId", state.manifestId);
            values.add(row);
        }
        return values;
    }

    private synchronized Map<String, Object> scan(Map<String, String> request) throws IOException {
```

with:

```java
            row.put("lastEventId", state.lastEventId);
            row.put("manifestId", state.manifestId);
            row.put("receivedUtc", state.receivedUtc);
            row.put("receivedDate", TimeFormat.date(state.receivedUtc, ZoneId.systemDefault()));
            values.add(row);
        }
        return values;
    }

    private synchronized Map<String, Object> scan(Map<String, String> request) throws IOException {
```

- [ ] **Step 2: Add fields to `packageMaps()`**

In the same file, in the static `packageMaps(List<PackageState> source)` method, replace:

```java
            row.put("lastEventId", state.lastEventId);
            row.put("manifestId", state.manifestId);
            values.add(row);
        }
        return values;
    }

    private static Map<String, Object> message(String text) {
```

with:

```java
            row.put("lastEventId", state.lastEventId);
            row.put("manifestId", state.manifestId);
            row.put("receivedUtc", state.receivedUtc);
            row.put("receivedDate", TimeFormat.date(state.receivedUtc, ZoneId.systemDefault()));
            values.add(row);
        }
        return values;
    }

    private static Map<String, Object> message(String text) {
```

- [ ] **Step 3: Run to verify it compiles and all Java tests pass**

Run from `apps/commercial-tracking-java/`: `powershell -File build.ps1 -SkipFrontend`
Expected: build succeeds; `ProjectionTest: PASS`, `TimeFormatTest: PASS`, and all pre-existing test lines still pass. (`TimeFormat.date` returns `""` for empty `receivedUtc`, so rows for never-received packages carry empty strings, never null.)

- [ ] **Step 4: Manual/integration check (note only — no automated harness)**

After the frontend tasks land, launch the app, receive a package, and confirm the `/state` payload's `packages[]` and `session[]` rows include a `receivedUtc` (ISO instant) and a `receivedDate` (`yyyy-MM-dd` in host zone). Record this as an integration verification step; it is not part of the automated suite because the maps are private.

- [ ] **Step 5: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java
git commit -m "feat(server): expose receivedUtc and host-zone receivedDate on package maps"
```

---

### Task 4: `SharedConfigManager` — add `timeFormat`, drop `operationalTimeZone` requirement

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/SharedConfigManager.java`
- Modify: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/SharedConfigManagerTest.java`

**Interfaces:**
- Produces: `validate` accepts only `12h`/`24h` for `timeFormat` (default `12h`), no longer requires or validates `operationalTimeZone` (a legacy value is ignored, not rejected); `defaults()` includes `timeFormat=12h` and omits `operationalTimeZone`.
- Consumes: existing `value`/`integer` helpers.

- [ ] **Step 1: Update the test (failing)**

Replace the entire body of `apps/commercial-tracking-java/src/test/java/org/commercialtracking/SharedConfigManagerTest.java` with:

```java
package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SharedConfigManagerTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("commercial-config-test-");
        SharedConfigManager manager = new SharedConfigManager(root);
        Map<String, String> first = settings("Main Receiving", "12h", "5", "false");
        manager.save(first);
        Map<String, String> second = settings("Mailroom", "24h", "10", "true");
        manager.save(second);
        check("Mailroom".equals(manager.reload().values.get("locations")), "new settings active");
        check("24h".equals(manager.reload().values.get("timeFormat")), "time format persisted");
        Files.write(root.resolve("configuration").resolve("application.json"), "{broken".getBytes(StandardCharsets.UTF_8));
        SharedConfigManager.State retained = manager.reload();
        check("Mailroom".equals(retained.values.get("locations")), "last valid settings retained");
        check(retained.error.length() > 0, "invalid synchronized settings reported");
        manager.rollback();
        check("Main Receiving".equals(manager.reload().values.get("locations")), "prior version restored");

        // A configuration WITHOUT operationalTimeZone must validate (the setting was removed).
        SharedConfigManager.validate(settings("Dock", "12h", "5", "false"));

        // A configuration still carrying a legacy operationalTimeZone must be accepted (ignored, not rejected).
        Map<String, String> legacy = settings("Dock", "12h", "5", "false");
        legacy.put("operationalTimeZone", "Not/AZone");
        SharedConfigManager.validate(legacy);

        // timeFormat gate: invalid rejected; both 12h and 24h accepted.
        boolean rejectedFormat = false;
        try { SharedConfigManager.validate(settings("Dock", "36h", "5", "false")); }
        catch (IllegalArgumentException ex) { rejectedFormat = true; }
        check(rejectedFormat, "invalid time format rejected");
        SharedConfigManager.validate(settings("Dock", "12h", "5", "false"));
        SharedConfigManager.validate(settings("Dock", "24h", "5", "false"));

        boolean invalid = false;
        try { manager.save(settings("", "12h", "0", "maybe")); }
        catch (IllegalArgumentException ex) { invalid = true; }
        check(invalid, "invalid proposal rejected");
        System.out.println("SharedConfigManagerTest: PASS");
    }

    private static Map<String, String> settings(String locations, String timeFormat, String pending, String retain) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("schemaVersion", "1");
        values.put("locations", locations);
        values.put("timeFormat", timeFormat);
        values.put("pendingAttentionMinutes", pending);
        values.put("retainRawBarcode", retain);
        return values;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run from `apps/commercial-tracking-java/`: `powershell -File build.ps1 -SkipFrontend`
Expected: `SharedConfigManagerTest` fails — the current `validate` still rejects the empty/invalid `operationalTimeZone` (`java.time.ZoneId.of("")`) inside `settings("Dock", "12h", ...)` and does not gate `timeFormat`, so the `36h` case is not rejected.

- [ ] **Step 3: Write minimal implementation**

In `apps/commercial-tracking-java/src/main/java/org/commercialtracking/SharedConfigManager.java`, replace the `validate` method's zone block. Replace:

```java
        String locations = value(values, "locations", "");
        if (locations.length() == 0 || locations.length() > 500) throw new IllegalArgumentException("At least one valid location is required.");
        String zone = value(values, "operationalTimeZone", "");
        try { java.time.ZoneId.of(zone); } catch (Exception ex) { throw new IllegalArgumentException("Invalid operational time zone."); }
        int pending = integer(values, "pendingAttentionMinutes", 5);
```

with:

```java
        String locations = value(values, "locations", "");
        if (locations.length() == 0 || locations.length() > 500) throw new IllegalArgumentException("At least one valid location is required.");
        String timeFormat = value(values, "timeFormat", "12h");
        if (!"12h".equals(timeFormat) && !"24h".equals(timeFormat)) throw new IllegalArgumentException("Time format must be 12h or 24h.");
        int pending = integer(values, "pendingAttentionMinutes", 5);
```

Then replace the `operationalTimeZone` line in `defaults()`. Replace:

```java
        values.put("locations", "Main Receiving|Loading Dock|Mailroom|Warehouse");
        values.put("operationalTimeZone", java.time.ZoneId.systemDefault().getId());
        values.put("pendingAttentionMinutes", "5");
```

with:

```java
        values.put("locations", "Main Receiving|Loading Dock|Mailroom|Warehouse");
        values.put("timeFormat", "12h");
        values.put("pendingAttentionMinutes", "5");
```

(`java.time` remains used via `java.time.Instant` in `save`/`backup`; no import changes are required — the removed references were fully qualified, not imports.)

- [ ] **Step 4: Run to verify it passes**

Run from `apps/commercial-tracking-java/`: `powershell -File build.ps1 -SkipFrontend`
Expected: `SharedConfigManagerTest: PASS`; build succeeds.

- [ ] **Step 5: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/SharedConfigManager.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/SharedConfigManagerTest.java
git commit -m "feat(config): add timeFormat setting and drop operationalTimeZone requirement"
```

---

### Task 5: `BrowserServer.saveSharedSettings` — persist `timeFormat`, stop requiring `operationalTimeZone`

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java`

**Interfaces:**
- Consumes: `value(request, key, fallback)` helper (trims), `required(request, key)` helper.
- Produces: the proposed shared-settings map includes `timeFormat` (defaulting to `12h`) and no longer includes/requires `operationalTimeZone`.

- [ ] **Step 1: Write minimal implementation**

In `apps/commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java`, in `saveSharedSettings`, replace:

```java
        Map<String, String> proposed = new LinkedHashMap<String, String>();
        proposed.put("schemaVersion", "1");
        proposed.put("locations", required(request, "locations"));
        proposed.put("operationalTimeZone", required(request, "operationalTimeZone"));
        proposed.put("pendingAttentionMinutes", required(request, "pendingAttentionMinutes"));
        proposed.put("retainRawBarcode", required(request, "retainRawBarcode"));
```

with:

```java
        Map<String, String> proposed = new LinkedHashMap<String, String>();
        proposed.put("schemaVersion", "1");
        proposed.put("locations", required(request, "locations"));
        proposed.put("timeFormat", value(request, "timeFormat", "12h"));
        proposed.put("pendingAttentionMinutes", required(request, "pendingAttentionMinutes"));
        proposed.put("retainRawBarcode", required(request, "retainRawBarcode"));
```

- [ ] **Step 2: Run to verify it compiles and all Java tests pass**

Run from `apps/commercial-tracking-java/`: `powershell -File build.ps1 -SkipFrontend`
Expected: build succeeds; all Java test lines pass. (The saved map now round-trips through `SharedConfigManager.save` → `validate`, which accepts the `12h` default from Task 4.)

- [ ] **Step 3: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/BrowserServer.java
git commit -m "feat(server): persist timeFormat and drop operationalTimeZone in saveSharedSettings"
```

---

### Task 6: Frontend `format.js` 12/24 support + `configureTimeFormat` + Node test

**Files:**
- Modify: `apps/commercial-tracking-java/frontend/src/format.js`
- Create: `apps/commercial-tracking-java/frontend/test/format.test.js`
- Modify: `apps/commercial-tracking-java/frontend/package.json`

**Interfaces:**
- Produces: `configureTimeFormat(pref)` — sets `hour12` (`pref === '24h'` → `false`, else `true`) and rebuilds the `Intl.DateTimeFormat` formatters; `formatDate(value, compact)` stays the single entry point (default `12h` until configured). Host zone and no-seconds behavior are preserved.

- [ ] **Step 1: Write the failing test**

Create `apps/commercial-tracking-java/frontend/test/format.test.js`:

```js
import assert from 'node:assert/strict'
import { formatDate, configureTimeFormat } from '../src/format.js'

// A fixed instant. Assertions are host-zone/locale independent: they compare the two
// preference outputs against each other rather than asserting a literal clock string.
const iso = '2026-08-04T21:30:45Z'

configureTimeFormat('12h')
const twelve = formatDate(iso)
configureTimeFormat('24h')
const twentyFour = formatDate(iso)

assert.equal(twelve.includes(':45'), false, '12h output has no seconds')
assert.equal(twentyFour.includes(':45'), false, '24h output has no seconds')
assert.notEqual(twelve, twentyFour, '12h and 24h render the same instant differently')

// Default / unknown preference falls back to 12h behavior.
configureTimeFormat(undefined)
assert.equal(formatDate(iso), twelve, 'default preference matches 12h output')
configureTimeFormat('anything-else')
assert.equal(formatDate(iso), twelve, 'unknown preference matches 12h output')

// Empty and unparseable inputs are handled without throwing.
assert.equal(formatDate(''), '—', 'empty value renders the em dash')
assert.equal(formatDate('not-a-date'), 'not-a-date', 'unparseable value passes through')

// Compact mode also honors the toggle and drops seconds.
configureTimeFormat('12h')
const compact12 = formatDate(iso, true)
configureTimeFormat('24h')
const compact24 = formatDate(iso, true)
assert.equal(compact12.includes(':45'), false, 'compact 12h has no seconds')
assert.equal(compact24.includes(':45'), false, 'compact 24h has no seconds')
assert.notEqual(compact12, compact24, 'compact mode differs by preference')

console.log('FormatTest: PASS')
```

- [ ] **Step 2: Wire the test into the npm test chain**

In `apps/commercial-tracking-java/frontend/package.json`, replace:

```json
    "test": "node test/scannerCapture.test.js"
```

with:

```json
    "test": "node test/scannerCapture.test.js && node test/format.test.js"
```

- [ ] **Step 3: Run to verify it fails**

Run from `apps/commercial-tracking-java/frontend/`: `npm test`
Expected: fails — `format.js` has no `configureTimeFormat` export, so the import throws `SyntaxError: does not provide an export named 'configureTimeFormat'`.

- [ ] **Step 4: Write minimal implementation**

Replace the entire contents of `apps/commercial-tracking-java/frontend/src/format.js` with:

```js
// Shared date formatting for records and timestamps, using the operator's locale and host zone.
// A module-level preference toggles 12h vs 24h; seconds are never displayed.
let hour12 = true

function buildDateTime() {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12 })
}

function buildShortTime() {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', hour12 })
}

let dateTime = buildDateTime()
let shortTime = buildShortTime()

// Called when shared settings load/refresh. pref === '24h' selects 24-hour time; anything else is 12-hour.
export function configureTimeFormat(pref) {
  hour12 = pref !== '24h'
  dateTime = buildDateTime()
  shortTime = buildShortTime()
}

export function formatDate(value, compact = false) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return compact ? shortTime.format(parsed) : dateTime.format(parsed)
}
```

- [ ] **Step 5: Run to verify it passes**

Run from `apps/commercial-tracking-java/frontend/`: `npm test`
Expected: `ScannerCaptureTest: PASS` then `FormatTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/frontend/src/format.js \
        apps/commercial-tracking-java/frontend/test/format.test.js \
        apps/commercial-tracking-java/frontend/package.json
git commit -m "feat(ui): honor 12h/24h time format with configureTimeFormat and drop seconds"
```

---

### Task 7: Frontend `locations.js` pure helper + Node test

**Files:**
- Create: `apps/commercial-tracking-java/frontend/src/locations.js`
- Create: `apps/commercial-tracking-java/frontend/test/locations.test.js`
- Modify: `apps/commercial-tracking-java/frontend/package.json`

**Interfaces:**
- Produces:
  - `parseLocations(pipeString) -> string[]` — split on `|`, trim, drop empties.
  - `serializeLocations(array) -> pipeString` — trim, drop empties, join with `|`.
  - `addLocation(array, candidate) -> {ok, list, error}` — trim; reject empty, `|`-containing, case-insensitive duplicate, or a result whose serialized length exceeds 500; on success returns the appended list.
- No DOM/React — pure and Node-importable.

- [ ] **Step 1: Write the failing test**

Create `apps/commercial-tracking-java/frontend/test/locations.test.js`:

```js
import assert from 'node:assert/strict'
import { parseLocations, serializeLocations, addLocation } from '../src/locations.js'

// parse
assert.deepEqual(parseLocations('Main| Dock |Mailroom'), ['Main', 'Dock', 'Mailroom'])
assert.deepEqual(parseLocations(''), [])
assert.deepEqual(parseLocations(null), [])
assert.deepEqual(parseLocations('A||B|'), ['A', 'B'])

// serialize
assert.equal(serializeLocations(['Main', ' Dock ', '']), 'Main|Dock')
assert.equal(serializeLocations([]), '')
assert.equal(serializeLocations(null), '')

// addLocation: happy path (and trims)
let result = addLocation(['Main'], 'Dock')
assert.equal(result.ok, true)
assert.deepEqual(result.list, ['Main', 'Dock'])
assert.equal(result.error, '')
result = addLocation(['Main'], '  Dock  ')
assert.equal(result.ok, true)
assert.deepEqual(result.list, ['Main', 'Dock'])

// addLocation: empty rejected, original list preserved
result = addLocation(['Main'], '   ')
assert.equal(result.ok, false)
assert.deepEqual(result.list, ['Main'])
assert.match(result.error, /enter a location/i)

// addLocation: pipe forbidden
result = addLocation(['Main'], 'Ma|in')
assert.equal(result.ok, false)
assert.match(result.error, /cannot contain/i)

// addLocation: case-insensitive duplicate rejected
result = addLocation(['Main'], 'main')
assert.equal(result.ok, false)
assert.match(result.error, /already exists/i)

// addLocation: total serialized length capped at 500
const nearLimit = 'x'.repeat(499)
result = addLocation([nearLimit], 'yy') // 499 + 1 (pipe) + 2 = 502 > 500
assert.equal(result.ok, false)
assert.match(result.error, /500 characters/i)
result = addLocation([], 'x'.repeat(500)) // exactly 500 is allowed
assert.equal(result.ok, true)

// addLocation: tolerates a null/undefined starting array
result = addLocation(undefined, 'First')
assert.equal(result.ok, true)
assert.deepEqual(result.list, ['First'])

console.log('LocationsTest: PASS')
```

- [ ] **Step 2: Wire the test into the npm test chain**

In `apps/commercial-tracking-java/frontend/package.json`, replace:

```json
    "test": "node test/scannerCapture.test.js && node test/format.test.js"
```

with:

```json
    "test": "node test/scannerCapture.test.js && node test/format.test.js && node test/locations.test.js"
```

- [ ] **Step 3: Run to verify it fails**

Run from `apps/commercial-tracking-java/frontend/`: `npm test`
Expected: fails — `../src/locations.js` does not exist (module resolution error).

- [ ] **Step 4: Write minimal implementation**

Create `apps/commercial-tracking-java/frontend/src/locations.js`:

```js
// Pure parse/serialize/validation for the receiving-locations chip editor.
// The persisted format is a pipe-delimited string; this module never touches the DOM.

export function parseLocations(pipeString) {
  if (!pipeString) return []
  return String(pipeString).split('|').map(value => value.trim()).filter(Boolean)
}

export function serializeLocations(array) {
  if (!array) return ''
  return array.map(value => String(value).trim()).filter(Boolean).join('|')
}

export function addLocation(array, candidate) {
  const list = Array.isArray(array) ? array.slice() : []
  const value = String(candidate == null ? '' : candidate).trim()
  if (value.length === 0) return { ok: false, list, error: 'Enter a location name.' }
  if (value.includes('|')) return { ok: false, list, error: 'Location names cannot contain the "|" character.' }
  if (list.some(existing => existing.toLowerCase() === value.toLowerCase())) {
    return { ok: false, list, error: 'That location already exists.' }
  }
  const next = list.concat(value)
  if (serializeLocations(next).length > 500) {
    return { ok: false, list, error: 'The combined locations exceed 500 characters.' }
  }
  return { ok: true, list: next, error: '' }
}
```

- [ ] **Step 5: Run to verify it passes**

Run from `apps/commercial-tracking-java/frontend/`: `npm test`
Expected: `ScannerCaptureTest: PASS`, `FormatTest: PASS`, then `LocationsTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/frontend/src/locations.js \
        apps/commercial-tracking-java/frontend/test/locations.test.js \
        apps/commercial-tracking-java/frontend/package.json
git commit -m "feat(ui): add pure locations parse/serialize/add helper with validation"
```

---

### Task 8: `SettingsWorkspace` chip editor + 12/24 toggle; configure time format on load

**Files:**
- Modify: `apps/commercial-tracking-java/frontend/src/main.jsx`

**Interfaces:**
- Consumes: `parseLocations`, `serializeLocations`, `addLocation` from `./locations`; `configureTimeFormat` from `./format`. MUI `Chip`, `Select`, `MenuItem`, `FormControl`, `InputLabel`, `TextField`, `Button`, `Stack`, `Box`, `Typography`, `Alert` are already imported in `main.jsx`.
- Produces: locations chip editor (Add button + Enter-to-add + deletable chips) backed by `locations.js`; a `timeFormat` `Select` bound to `shared.timeFormat`; the operational-time-zone field and its review-copy mention removed; `configureTimeFormat` called whenever `/state` loads so on-screen times honor the setting.

This React UI has no automated unit test (its pure logic is covered by Tasks 6–7). Verify with `npm run build` plus a manual check.

- [ ] **Step 1: Import the new helpers and call `configureTimeFormat` on load**

In `apps/commercial-tracking-java/frontend/src/main.jsx`, replace the format import:

```jsx
import { formatDate } from './format'
```

with:

```jsx
import { formatDate, configureTimeFormat } from './format'
import { parseLocations, serializeLocations, addLocation } from './locations'
```

Then, in the `refresh` callback, apply the loaded preference before storing state. Replace:

```jsx
  const refresh = useCallback(async (quiet = false) => {
    try {
      if (!quiet) setBusy(true)
      setState(await api.state())
    } catch (error) {
```

with:

```jsx
  const refresh = useCallback(async (quiet = false) => {
    try {
      if (!quiet) setBusy(true)
      const next = await api.state()
      configureTimeFormat(next?.sharedSettings?.timeFormat)
      setState(next)
    } catch (error) {
```

- [ ] **Step 2: Add chip-editor state and handlers to `SettingsWorkspace`**

In `SettingsWorkspace`, add the draft/error state and the add/remove handlers. Replace:

```jsx
  const [reviewing, setReviewing] = useState(false)
  useEffect(() => setScanner({ ...state.scannerSettings, deviceId: state.deviceId }), [state.scannerSettings, state.deviceId])
  useEffect(() => { setShared(state.sharedSettings); setReviewing(false) }, [state.sharedSettings])
  useEffect(() => setDisplayName(state.actorDisplayName || ''), [state.actorDisplayName])
  const update = (key, value) => setScanner(current => ({ ...current, [key]: value }))
```

with:

```jsx
  const [reviewing, setReviewing] = useState(false)
  const [locationDraft, setLocationDraft] = useState('')
  const [locationError, setLocationError] = useState('')
  useEffect(() => setScanner({ ...state.scannerSettings, deviceId: state.deviceId }), [state.scannerSettings, state.deviceId])
  useEffect(() => { setShared(state.sharedSettings); setReviewing(false) }, [state.sharedSettings])
  useEffect(() => setDisplayName(state.actorDisplayName || ''), [state.actorDisplayName])
  const update = (key, value) => setScanner(current => ({ ...current, [key]: value }))
  const locationList = parseLocations(shared.locations)
  const commitLocation = () => {
    const result = addLocation(locationList, locationDraft)
    if (!result.ok) { setLocationError(result.error); return }
    setReviewing(false)
    setShared(current => ({ ...current, locations: serializeLocations(result.list) }))
    setLocationDraft(''); setLocationError('')
  }
  const removeLocation = value => {
    setReviewing(false)
    setShared(current => ({ ...current, locations: serializeLocations(locationList.filter(item => item !== value)) }))
  }
```

- [ ] **Step 3: Replace the shared-settings `Stack` (locations field + time-zone field) with the chip editor + time toggle**

In `SettingsWorkspace`, replace the shared-settings stack:

```jsx
    <Stack spacing={2}><TextField label="Locations (separate with |)" value={shared.locations || ''} onChange={event => { setReviewing(false); setShared(current => ({ ...current, locations: event.target.value })) }} /><TextField label="Operational time zone" value={shared.operationalTimeZone || ''} onChange={event => { setReviewing(false); setShared(current => ({ ...current, operationalTimeZone: event.target.value })) }} /><TextField type="number" label="Pending attention threshold (minutes)" value={shared.pendingAttentionMinutes || 5} onChange={event => { setReviewing(false); setShared(current => ({ ...current, pendingAttentionMinutes: event.target.value })) }} /><FormControl><InputLabel>Retain raw barcode in events</InputLabel><Select label="Retain raw barcode in events" value={shared.retainRawBarcode || 'false'} onChange={event => { setReviewing(false); setShared(current => ({ ...current, retainRawBarcode: event.target.value })) }}><MenuItem value="false">No</MenuItem><MenuItem value="true">Yes</MenuItem></Select></FormControl></Stack>
```

with:

```jsx
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Receiving locations</Typography>
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
          {locationList.length ? locationList.map(value => <Chip key={value} label={value} onDelete={() => removeLocation(value)} />) : <Typography variant="body2" color="text.secondary">Add at least one receiving location.</Typography>}
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <TextField fullWidth label="Add location" value={locationDraft} error={!!locationError} helperText={locationError || 'Press Enter or Add to append a location.'} onChange={event => { setLocationDraft(event.target.value); setLocationError('') }} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); commitLocation() } }} />
          <Button variant="outlined" onClick={commitLocation}>Add</Button>
        </Stack>
      </Box>
      <FormControl sx={{ maxWidth: 240 }}><InputLabel>Time display</InputLabel><Select label="Time display" value={shared.timeFormat || '12h'} onChange={event => { setReviewing(false); setShared(current => ({ ...current, timeFormat: event.target.value })) }}><MenuItem value="12h">12-hour (1:30 PM)</MenuItem><MenuItem value="24h">24-hour (13:30)</MenuItem></Select></FormControl>
      <TextField type="number" label="Pending attention threshold (minutes)" value={shared.pendingAttentionMinutes || 5} onChange={event => { setReviewing(false); setShared(current => ({ ...current, pendingAttentionMinutes: event.target.value })) }} />
      <FormControl><InputLabel>Retain raw barcode in events</InputLabel><Select label="Retain raw barcode in events" value={shared.retainRawBarcode || 'false'} onChange={event => { setReviewing(false); setShared(current => ({ ...current, retainRawBarcode: event.target.value })) }}><MenuItem value="false">No</MenuItem><MenuItem value="true">Yes</MenuItem></Select></FormControl>
    </Stack>
```

- [ ] **Step 4: Update the review `Alert` copy to drop the operational time zone**

Replace:

```jsx
    {reviewing && <Alert severity="info" sx={{ mt: 2 }}>Review: locations, operational time zone, pending threshold, and barcode-retention policy will replace the effective shared values. The prior valid version will be retained and an audit event will be written.</Alert>}
```

with:

```jsx
    {reviewing && <Alert severity="info" sx={{ mt: 2 }}>Review: locations, time display, pending threshold, and barcode-retention policy will replace the effective shared values. The prior valid version will be retained and an audit event will be written.</Alert>}
```

The Save button already calls `onSaveShared(shared)`, and `shared` now carries `timeFormat` (a `Select` value defaulting to `12h`) and no longer surfaces `operationalTimeZone`; the App-level `onSaveShared` spreads the payload with `confirmed: 'true'` and the backend `saveSharedSettings` (Task 5) reads `timeFormat` and ignores any stray `operationalTimeZone`. No App-level change is required.

- [ ] **Step 5: Verify the frontend builds and tests pass**

Run from `apps/commercial-tracking-java/frontend/`:
`npm test` (expected: `ScannerCaptureTest: PASS`, `FormatTest: PASS`, `LocationsTest: PASS`)
`npm run build` (expected: Vite build succeeds with no errors — confirms the JSX and imports are valid).

- [ ] **Step 6: Manual check (note only)**

Launch the app, open Settings → Shared operational settings. Confirm: chips render for existing locations; typing a name + Enter or Add appends a chip; the `|` character, duplicates (case-insensitive), and empty input show inline errors; deleting a chip removes it; the Time display toggle switches on-screen times between 12h and 24h (no seconds shown); the operational-time-zone field is gone; Review → Confirm saves and round-trips the locations and `timeFormat` after refresh.

- [ ] **Step 7: Commit**

```bash
git add apps/commercial-tracking-java/frontend/src/main.jsx
git commit -m "feat(ui): locations chip editor and 12/24 time toggle in settings"
```

---

## Self-Review

**Spec coverage:**
- **§0 (received date):** `PackageState.receivedUtc` + `Projection` first-receive capture (never overwritten) → Task 1; package maps expose `receivedUtc` + host-zone `receivedDate` → Task 3. ✓
- **§6 `SharedConfigManager`:** `timeFormat` (default `12h`, validate ∈ {`12h`,`24h`}); `operationalTimeZone` no longer required/validated (ignored if present); `defaults()` drops it → Task 4. ✓
- **§6 host time / `saveSharedSettings`:** persist `timeFormat`, stop requiring `operationalTimeZone` → Task 5. Package-map `receivedDate` uses `ZoneId.systemDefault()` → Task 3. ✓
- **§6 `format.js`:** host-zone, no-seconds retained; `configureTimeFormat` sets `hour12`; `formatDate` single entry point, default 12h → Task 6; called on state load → Task 8. ✓
- **§6 locations chip editor:** pure `locations.js` (parse/serialize/add with trim, non-empty, no `|`, no case-insensitive duplicate, ≤500) → Task 7; MUI chip editor wired in → Task 8. ✓
- **§6 settings UI:** 12/24 toggle added; operational-time-zone field and its review copy removed → Task 8. ✓
- **Test wiring:** `ProjectionTest` and `TimeFormatTest` added to `build.ps1` (Tasks 1, 2); `format.test.js` and `locations.test.js` added to the `npm test` chain (Tasks 6, 7); `SharedConfigManagerTest` updated for the new gate (Task 4). ✓

**Placeholder scan:** No "TBD"/"handle validation later"/"similar to". Every code step shows complete, runnable Java/JS with exact old→new replacements. ✓

**Type/name consistency with the shared contract:**
- `TimeFormat.date(String, ZoneId)`, `TimeFormat.prepared(String, ZoneId, String)`, `TimeFormat.utcMinute(String)` — signatures identical between the impl (Task 2), the test (Task 2), and the callers (Task 3). ✓
- `PackageState.receivedUtc` (String, default "", copied) used consistently in Task 1 impl/test and Task 3 map rows. ✓
- Package-map keys `receivedUtc` / `receivedDate` added in both `sessionPackageMaps` and `packageMaps` (Task 3). ✓
- Shared setting `timeFormat` values `12h`/`24h` are consistent across `SharedConfigManager` (Task 4), `saveSharedSettings` (Task 5), `format.js` (`pref === '24h'`, Task 6), and the settings `Select` (Task 8). ✓
- `parseLocations` / `serializeLocations` / `addLocation({ok,list,error})` signatures identical between impl (Task 7), test (Task 7), and consumer (Task 8). ✓
- `configureTimeFormat(pref)` exported by `format.js` (Task 6) and called with `state.sharedSettings?.timeFormat` (Task 8). ✓

**Scope guard:** `manifest()`, `report()`, `ManifestWorkspace`, and `ReportsWorkspace` are untouched. The persisted `locations` pipe format and event-log schema are unchanged (`receivedUtc` is a derived projection field only).

**Risks / cross-plan sequencing:**
- Removing `operationalTimeZone` from `SharedConfigManager.defaults()` and `validate` (Task 4) means `BrowserServer.manifest()` (line ~536) and `report()` still read `values.get("operationalTimeZone")`, which now returns `null` for fresh/migrated configs. Those call sites are owned by Plans 2 and 3 and are exercised only by manifest/report generation (integration/manual paths), not by the automated Java suite — so `build.ps1 -SkipFrontend` stays green. **However, if this plan merges before Plans 2–3, live manifest/report generation can NPE on the null zone.** Mitigation: sequence Plan 4 alongside or after Plans 2–3, or land their host-time switch in the same release. This is called out so the executor coordinates the merge order.
- `format.test.js` assertions are host-zone/locale independent by design (they compare 12h vs 24h outputs rather than asserting a literal clock string), so they remain deterministic across CI environments.
- `TimeFormatTest` pins `America/New_York` and a fixed instant for a deterministic 5:30 PM / 17:30 expectation; it does not rely on the host's default zone.
