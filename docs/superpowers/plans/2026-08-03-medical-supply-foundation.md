# Medical Supply Tracking — Plan 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the `medical-supply-java` project with a portable Java 8 build and a headless, self-testable event-sourced storage foundation (JSON library, event model, event store, local index, per-user config).

**Architecture:** A new sibling project `medical-supply-java/` reusing the `commercial-tracking-java` portability blueprint (no Maven/Gradle, `build.ps1`, `javac --release 8`, framework-free `main()` tests). This plan delivers only the headless core: a small recursive JSON reader/writer (objects/arrays/nesting, so it later serves the GUDID client), a generic `SupplyEvent` (envelope + string payload map), an atomic append/replay `EventStore` on a OneDrive-synced folder, a rebuildable `LocalEventIndex`, and `AppConfig`. No domain logic, no UI — those are Plans 2 and 3.

**Tech Stack:** Java 8 (compiled with `javac --release 8`), PowerShell build script, Java SE APIs only (`java.nio.file`, `java.security.MessageDigest`, `java.time`). No third-party libraries.

## Global Constraints

- Target Java 8 bytecode: compile every source with `javac --release 8`. Use only Java SE 8 APIs.
- No Maven, Gradle, Node, or npm in this plan. Build is `build.ps1` (PowerShell) only.
- No third-party Java dependencies. JSON is hand-written in this project.
- Package root: `org.medsupply`.
- Per-user local data root: `%LOCALAPPDATA%\MedicalSupply` (fallback `user.home` when unset). Override via system property `medsupply.localBase`.
- Shared store root is an operator-selected OneDrive-synced folder; the app only ever appends its own immutable files there.
- Event files never exceed 1 MB; reject larger on read.
- Tests are framework-free: each test class is `public final class XxxTest` with a `public static void main(String[] args)` that throws `AssertionError` on failure and prints `XxxTest: PASS` on success. `build.ps1` runs each and fails the build on non-zero exit.
- All shared JSON is UTF-8, LF-terminated.

---

### Task 1: Project scaffold and Java 8 build

**Files:**
- Create: `medical-supply-java/.gitignore`
- Create: `medical-supply-java/build.ps1`
- Create: `medical-supply-java/run-medical-supply.cmd`
- Create: `medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/SelfTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `MedicalSupplyApp.main(String[])` — entry point; `--self-test` runs `SelfTest.run()` and prints `MedicalSupply self-test: PASS` (exit 0) or `... FAIL - <msg>` (exit 1).
  - `SelfTest.run()` — `static void`, throws `Exception` on failure. Grows in later tasks; starts trivial.

- [ ] **Step 1: Create `.gitignore`**

```gitignore
/build/
/dist/
/dist-review/
```

- [ ] **Step 2: Create the minimal entry point**

`medical-supply-java/src/main/java/org/medsupply/MedicalSupplyApp.java`:

```java
package org.medsupply;

public final class MedicalSupplyApp {
    private MedicalSupplyApp() {}

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
        System.out.println("MedicalSupply foundation build. Use --self-test.");
    }
}
```

- [ ] **Step 3: Create the initially-trivial self test**

`medical-supply-java/src/main/java/org/medsupply/SelfTest.java`:

```java
package org.medsupply;

public final class SelfTest {
    private SelfTest() {}

    public static void run() throws Exception {
        // Grows in later tasks. Trivial invariant for now.
        if (!"1".equals(SupplyMeta.SCHEMA_VERSION)) {
            throw new AssertionError("Unexpected schema version");
        }
    }
}
```

- [ ] **Step 4: Create the schema-version constant referenced above**

`medical-supply-java/src/main/java/org/medsupply/SupplyMeta.java`:

```java
package org.medsupply;

public final class SupplyMeta {
    private SupplyMeta() {}
    public static final String SCHEMA_VERSION = "1";
}
```

(Add `Create: .../SupplyMeta.java` to this task's file list.)

- [ ] **Step 5: Create the build script**

`medical-supply-java/build.ps1`:

```powershell
param(
    [switch]$SkipTests,
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$buildRoot = Join-Path $projectRoot "build"
$classes = Join-Path $buildRoot "classes"
$testClasses = Join-Path $buildRoot "test-classes"
$dist = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
}

if (Test-Path $buildRoot) { Remove-Item -LiteralPath $buildRoot -Recurse -Force }
if (Test-Path $dist) { Remove-Item -LiteralPath $dist -Recurse -Force }
New-Item -ItemType Directory -Path $classes | Out-Null
New-Item -ItemType Directory -Path $testClasses | Out-Null
New-Item -ItemType Directory -Path $dist | Out-Null

$mainSources = Get-ChildItem -Path (Join-Path $projectRoot "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& javac --release 8 -encoding UTF-8 -d $classes $mainSources
if ($LASTEXITCODE -ne 0) { throw "Main compilation failed." }

$testJavaRoot = Join-Path $projectRoot "src\test\java"
if (Test-Path $testJavaRoot) {
    $testSources = Get-ChildItem -Path $testJavaRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac --release 8 -encoding UTF-8 -cp $classes -d $testClasses $testSources
    if ($LASTEXITCODE -ne 0) { throw "Test compilation failed." }
    if (-not $SkipTests) {
        $tests = Get-ChildItem -Path $testClasses -Recurse -Filter *Test.class |
            ForEach-Object { $_.FullName.Substring($testClasses.Length + 1).Replace('\','.').Replace('.class','') }
        foreach ($t in $tests) {
            & java -cp "$classes;$testClasses" $t
            if ($LASTEXITCODE -ne 0) { throw "Test failed: $t" }
        }
    }
}

$manifest = Join-Path $buildRoot "MANIFEST.MF"
@(
    "Manifest-Version: 1.0"
    "Main-Class: org.medsupply.MedicalSupplyApp"
    "Implementation-Title: Medical Supply RC"
    "Implementation-Version: 0.1.0-foundation"
    ""
) | Set-Content -LiteralPath $manifest -Encoding ascii

$jar = Join-Path $dist "MedicalSupply-RC.jar"
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
if ($null -ne $jarCommand) {
    $jarTool = $jarCommand.Source
} else {
    $javaSettings = (& java -XshowSettings:properties -version 2>&1 | Out-String)
    $javaHomeMatch = [regex]::Match($javaSettings, "java\.home\s*=\s*(.+)")
    if (-not $javaHomeMatch.Success) { throw "Could not locate the JDK jar tool." }
    $jarTool = Join-Path $javaHomeMatch.Groups[1].Value.Trim() "bin\jar.exe"
}
& $jarTool cfm $jar $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed." }

Copy-Item -LiteralPath (Join-Path $projectRoot "run-medical-supply.cmd") -Destination $dist
Write-Host "Built: $jar"
```

- [ ] **Step 6: Create the launcher**

`medical-supply-java/run-medical-supply.cmd`:

```bat
@echo off
setlocal
set DIR=%~dp0
java -jar "%DIR%MedicalSupply-RC.jar" %*
if errorlevel 1 pause
endlocal
```

- [ ] **Step 7: Build and verify the JAR runs the self-test on Java 8**

Run:
```
powershell -File medical-supply-java/build.ps1
java -jar medical-supply-java/dist/MedicalSupply-RC.jar --self-test
```
Expected: build prints `Built: ...MedicalSupply-RC.jar`; the run prints `MedicalSupply self-test: PASS`.

- [ ] **Step 8: Commit**

```bash
git add medical-supply-java/
git commit -m "feat(medsupply): project scaffold and Java 8 build"
```

---

### Task 2: Minimal recursive JSON library

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/Json.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/JsonTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `Json.parse(String) -> Object` — returns `LinkedHashMap<String,Object>` for objects, `ArrayList<Object>` for arrays, `String`, `Double`, `Boolean`, or `null`. Throws `IllegalArgumentException` on malformed input.
  - `Json.write(Object) -> String` — serializes the same value types; escapes strings; renders whole-number doubles without a trailing `.0`.
  - `Json.asMap(Object) -> Map<String,Object>` and `Json.asList(Object) -> List<Object>` — safe casts returning empty collections on type mismatch/null.
  - `Json.str(Map<String,Object>, String key) -> String` — value coerced to string, `""` when absent/null.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/JsonTest.java`:

```java
package org.medsupply;

import java.util.List;
import java.util.Map;

public final class JsonTest {
    public static void main(String[] args) {
        roundTripScalarsAndNesting();
        parsesGudidShapedResponse();
        writesWholeNumbersWithoutDecimal();
        escapesStrings();
        System.out.println("JsonTest: PASS");
    }

    private static void roundTripScalarsAndNesting() {
        String json = "{\"a\":\"x\",\"n\":3,\"b\":true,\"z\":null,\"arr\":[1,2,{\"k\":\"v\"}]}";
        Object parsed = Json.parse(json);
        Map<String, Object> m = Json.asMap(parsed);
        check("x".equals(Json.str(m, "a")), "a");
        check("3".equals(Json.str(m, "n")), "n");
        check(Boolean.TRUE.equals(m.get("b")), "b");
        check(m.containsKey("z") && m.get("z") == null, "z null");
        List<Object> arr = Json.asList(m.get("arr"));
        check(arr.size() == 3, "arr size");
        check("v".equals(Json.str(Json.asMap(arr.get(2)), "k")), "nested k");
    }

    private static void parsesGudidShapedResponse() {
        String json = "{\"device\":{\"brandName\":\"XIENCE\",\"companyName\":\"ABBOTT\","
                + "\"gmdnTerms\":{\"gmdn\":[{\"gmdnPTName\":\"Coronary stent\"}]}}}";
        Map<String, Object> root = Json.asMap(Json.parse(json));
        Map<String, Object> device = Json.asMap(root.get("device"));
        check("XIENCE".equals(Json.str(device, "brandName")), "brandName");
        check("ABBOTT".equals(Json.str(device, "companyName")), "companyName");
    }

    private static void writesWholeNumbersWithoutDecimal() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("q", Double.valueOf(5));
        check("{\"q\":5}".equals(Json.write(m)), "whole number: " + Json.write(m));
    }

    private static void escapesStrings() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("s", "a\"b\\c\n");
        String written = Json.write(m);
        check(written.equals("{\"s\":\"a\\\"b\\\\c\\n\"}"), "escape: " + written);
        check("a\"b\\c\n".equals(Json.str(Json.asMap(Json.parse(written)), "s")), "escape roundtrip");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — test compilation fails with `cannot find symbol ... Json`.

- [ ] **Step 3: Write the implementation**

`medical-supply-java/src/main/java/org/medsupply/Json.java`:

```java
package org.medsupply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("null JSON");
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (!p.atEnd()) throw new IllegalArgumentException("Trailing JSON content at " + p.pos);
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value
                : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return "";
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.rint(d) && !Double.isInfinite(d)) return Long.toString((long) d);
            return Double.toString(d);
        }
        return String.valueOf(value);
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(e.getKey()));
                out.append(':');
                writeValue(out, e.getValue());
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) out.append(',');
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) out.append(Long.toString((long) d));
            else out.append(Double.toString(d));
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object readValue() {
            skipWs();
            if (atEnd()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't': case 'f': return readBoolean();
                case 'n': expect("null"); return null;
                default: return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            pos++; // {
            skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                if (peek() != '"') throw new IllegalArgumentException("Expected key at " + pos);
                String key = readString();
                skipWs();
                if (peek() != ':') throw new IllegalArgumentException("Expected ':' at " + pos);
                pos++;
                map.put(key, readValue());
                skipWs();
                char n = peek();
                if (n == ',') { pos++; continue; }
                if (n == '}') { pos++; break; }
                throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
            }
            return map;
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<Object>();
            pos++; // [
            skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(readValue());
                skipWs();
                char n = peek();
                if (n == ',') { pos++; continue; }
                if (n == ']') { pos++; break; }
                throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
            }
            return list;
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) break;
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw new IllegalArgumentException("Bad \\u escape");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw new IllegalArgumentException("Bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Boolean readBoolean() {
            if (s.charAt(pos) == 't') { expect("true"); return Boolean.TRUE; }
            expect("false");
            return Boolean.FALSE;
        }

        private Double readNumber() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            if (pos == start) throw new IllegalArgumentException("Invalid number at " + start);
            return Double.valueOf(s.substring(start, pos));
        }

        private void expect(String literal) {
            if (!s.regionMatches(pos, literal, 0, literal.length()))
                throw new IllegalArgumentException("Expected '" + literal + "' at " + pos);
            pos += literal.length();
        }

        private char peek() {
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(pos);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `JsonTest: PASS` and `Built: ...`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/Json.java medical-supply-java/src/test/java/org/medsupply/JsonTest.java
git commit -m "feat(medsupply): recursive JSON reader/writer"
```

---

### Task 3: SupplyEvent model and JSON serialization

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/SupplyEvent.java`
- Create: `medical-supply-java/src/main/java/org/medsupply/SupplyEventJson.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/SupplyEventJsonTest.java`

**Interfaces:**
- Consumes: `Json` (Task 2), `SupplyMeta.SCHEMA_VERSION` (Task 1).
- Produces:
  - `SupplyEvent` — public fields: `String schemaVersion` (default `SupplyMeta.SCHEMA_VERSION`), `eventId` (default random UUID), `eventType`, `occurredUtc` (ISO-8601, default now), `recordedUtc` (default now), `deviceId`, `sessionId`, `actor`; and `Map<String,String> payload` (a `LinkedHashMap`). Method `String payload(String key)` returns `""` when absent.
  - `SupplyEventJson.write(SupplyEvent) -> String` (envelope keys + nested `payload` object; trailing `\n`).
  - `SupplyEventJson.read(String) -> SupplyEvent`.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/SupplyEventJsonTest.java`:

```java
package org.medsupply;

public final class SupplyEventJsonTest {
    public static void main(String[] args) {
        roundTrip();
        readToleratesMissingPayload();
        System.out.println("SupplyEventJsonTest: PASS");
    }

    private static void roundTrip() {
        SupplyEvent e = new SupplyEvent();
        e.eventType = "STOCK_RECEIVED";
        e.deviceId = "WS-1";
        e.actor = "DOM\\alice";
        e.occurredUtc = "2026-08-03T10:00:00Z";
        e.recordedUtc = "2026-08-03T10:00:01Z";
        e.payload.put("gtin", "00380740000010");
        e.payload.put("lot", "AB\"12");
        e.payload.put("quantityDelta", "5");

        SupplyEvent back = SupplyEventJson.read(SupplyEventJson.write(e));
        check("STOCK_RECEIVED".equals(back.eventType), "type");
        check(e.eventId.equals(back.eventId), "id");
        check("WS-1".equals(back.deviceId), "device");
        check("2026-08-03T10:00:00Z".equals(back.occurredUtc), "occurred");
        check("00380740000010".equals(back.payload("gtin")), "gtin");
        check("AB\"12".equals(back.payload("lot")), "lot escaped");
        check("5".equals(back.payload("quantityDelta")), "delta");
    }

    private static void readToleratesMissingPayload() {
        String json = "{\"schemaVersion\":1,\"eventId\":\"x\",\"eventType\":\"T\","
                + "\"occurredUtc\":\"2026-01-01T00:00:00Z\",\"recordedUtc\":\"2026-01-01T00:00:00Z\","
                + "\"deviceId\":\"D\",\"sessionId\":\"S\",\"actor\":\"A\"}";
        SupplyEvent e = SupplyEventJson.read(json);
        check("T".equals(e.eventType), "type without payload");
        check(e.payload.isEmpty(), "empty payload");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... SupplyEvent`.

- [ ] **Step 3: Write `SupplyEvent`**

`medical-supply-java/src/main/java/org/medsupply/SupplyEvent.java`:

```java
package org.medsupply;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SupplyEvent {
    public String schemaVersion = SupplyMeta.SCHEMA_VERSION;
    public String eventId = UUID.randomUUID().toString();
    public String eventType = "";
    public String occurredUtc = Instant.now().toString();
    public String recordedUtc = Instant.now().toString();
    public String deviceId = "";
    public String sessionId = "";
    public String actor = "";
    public final Map<String, String> payload = new LinkedHashMap<String, String>();

    public String payload(String key) {
        String value = payload.get(key);
        return value == null ? "" : value;
    }
}
```

- [ ] **Step 4: Write `SupplyEventJson`**

`medical-supply-java/src/main/java/org/medsupply/SupplyEventJson.java`:

```java
package org.medsupply;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SupplyEventJson {
    private SupplyEventJson() {}

    public static String write(SupplyEvent event) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("schemaVersion", Integer.valueOf(parseIntOr(event.schemaVersion, 1)));
        root.put("eventId", event.eventId);
        root.put("eventType", event.eventType);
        root.put("occurredUtc", event.occurredUtc);
        root.put("recordedUtc", event.recordedUtc);
        root.put("deviceId", event.deviceId);
        root.put("sessionId", event.sessionId);
        root.put("actor", event.actor);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> field : event.payload.entrySet())
            payload.put(field.getKey(), field.getValue());
        root.put("payload", payload);
        return Json.write(root) + "\n";
    }

    public static SupplyEvent read(String json) {
        if (json == null || json.length() > 1024 * 1024)
            throw new IllegalArgumentException("Invalid JSON size");
        Map<String, Object> root = Json.asMap(Json.parse(json));
        SupplyEvent e = new SupplyEvent();
        e.schemaVersion = Json.str(root, "schemaVersion");
        e.eventId = Json.str(root, "eventId");
        e.eventType = Json.str(root, "eventType");
        e.occurredUtc = Json.str(root, "occurredUtc");
        e.recordedUtc = Json.str(root, "recordedUtc");
        if (e.recordedUtc.length() == 0) e.recordedUtc = e.occurredUtc;
        e.deviceId = Json.str(root, "deviceId");
        e.sessionId = Json.str(root, "sessionId");
        e.actor = Json.str(root, "actor");
        Map<String, Object> payload = Json.asMap(root.get("payload"));
        for (Map.Entry<String, Object> entry : payload.entrySet())
            e.payload.put(entry.getKey(), entry.getValue() == null ? "" : Json.str(payload, entry.getKey()));
        return e;
    }

    private static int parseIntOr(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return fallback; }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `SupplyEventJsonTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/SupplyEvent.java medical-supply-java/src/main/java/org/medsupply/SupplyEventJson.java medical-supply-java/src/test/java/org/medsupply/SupplyEventJsonTest.java
git commit -m "feat(medsupply): SupplyEvent model and JSON serialization"
```

---

### Task 4: Rebuildable local event index

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/LocalEventIndex.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/LocalEventIndexTest.java`

**Interfaces:**
- Consumes: `Json` (Task 2).
- Produces:
  - `LocalEventIndex.Entry` — `public final String relativePath; long size; long modifiedMillis; String fileHash; String json;` with a public constructor `Entry(String relativePath, long size, long modifiedMillis, String fileHash, String json)`.
  - `new LocalEventIndex(Path localRoot)` — loads/creates the cache under `localRoot/index/events-index.json`.
  - `Entry find(String relativePath, long size, long modifiedMillis)` — returns the cached entry when path+size+modified all match, else `null`.
  - `void replace(Map<String,Entry> entries)` — atomically rewrites the cache.
  - `void clear()` — deletes the cache.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/LocalEventIndexTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalEventIndexTest {
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("medsupply-index");
        LocalEventIndex index = new LocalEventIndex(tmp);
        check(index.find("events/a.json", 10, 100) == null, "empty miss");

        Map<String, LocalEventIndex.Entry> entries = new LinkedHashMap<String, LocalEventIndex.Entry>();
        entries.put("events/a.json", new LocalEventIndex.Entry("events/a.json", 10, 100, "hash", "{\"x\":1}"));
        index.replace(entries);

        LocalEventIndex reopened = new LocalEventIndex(tmp);
        LocalEventIndex.Entry hit = reopened.find("events/a.json", 10, 100);
        check(hit != null && "{\"x\":1}".equals(hit.json), "persisted hit");
        check(reopened.find("events/a.json", 11, 100) == null, "size mismatch miss");

        reopened.clear();
        check(new LocalEventIndex(tmp).find("events/a.json", 10, 100) == null, "cleared");
        System.out.println("LocalEventIndexTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... LocalEventIndex`.

- [ ] **Step 3: Write the implementation**

`medical-supply-java/src/main/java/org/medsupply/LocalEventIndex.java`:

```java
package org.medsupply;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalEventIndex {
    private final Path file;
    private final Map<String, Entry> entries = new HashMap<String, Entry>();

    public LocalEventIndex(Path localRoot) throws IOException {
        Files.createDirectories(localRoot.resolve("index"));
        this.file = localRoot.resolve("index").resolve("events-index.json");
        load();
    }

    public synchronized Entry find(String relativePath, long size, long modifiedMillis) {
        Entry entry = entries.get(relativePath);
        if (entry == null) return null;
        if (entry.size != size || entry.modifiedMillis != modifiedMillis) return null;
        return entry;
    }

    public synchronized void replace(Map<String, Entry> updated) throws IOException {
        entries.clear();
        entries.putAll(updated);
        List<Object> rows = new ArrayList<Object>();
        for (Entry e : updated.values()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("relativePath", e.relativePath);
            row.put("size", Double.valueOf(e.size));
            row.put("modifiedMillis", Double.valueOf(e.modifiedMillis));
            row.put("fileHash", e.fileHash);
            row.put("json", e.json);
            rows.add(row);
        }
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        Files.write(partial, Json.write(rows).getBytes(StandardCharsets.UTF_8));
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized void clear() throws IOException {
        entries.clear();
        Files.deleteIfExists(file);
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            for (Object row : Json.asList(Json.parse(text))) {
                Map<String, Object> map = Json.asMap(row);
                String relative = Json.str(map, "relativePath");
                Entry entry = new Entry(relative,
                        (long) doubleValue(map.get("size")),
                        (long) doubleValue(map.get("modifiedMillis")),
                        Json.str(map, "fileHash"),
                        Json.str(map, "json"));
                entries.put(relative, entry);
            }
        } catch (RuntimeException ignored) {
            entries.clear(); // corrupt cache is rebuildable; ignore and start fresh
        }
    }

    private static double doubleValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    public static final class Entry {
        public final String relativePath;
        public final long size;
        public final long modifiedMillis;
        public final String fileHash;
        public final String json;

        public Entry(String relativePath, long size, long modifiedMillis, String fileHash, String json) {
            this.relativePath = relativePath;
            this.size = size;
            this.modifiedMillis = modifiedMillis;
            this.fileHash = fileHash;
            this.json = json;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `LocalEventIndexTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/LocalEventIndex.java medical-supply-java/src/test/java/org/medsupply/LocalEventIndexTest.java
git commit -m "feat(medsupply): rebuildable local event index"
```

---

### Task 5: Event store (atomic append, replay, dedup, pending retry)

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/EventStore.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/EventStoreTest.java`

**Interfaces:**
- Consumes: `SupplyEvent`, `SupplyEventJson` (Task 3), `LocalEventIndex` (Task 4).
- Produces:
  - `new EventStore(Path sharedRoot, Path localRoot)` — creates `events/`, `reports/`, `configuration/`, `diagnostics/` under `sharedRoot` and `pending/` under `localRoot`.
  - `Path append(SupplyEvent) -> Path` — writes a local pending temp, fsyncs, then atomically publishes an immutable file under `sharedRoot/events/YYYY/MM/`.
  - `LoadResult loadAll()` — walks `events/`, dedups by `eventId` (same id + differing content hash → error), returns events sorted by `occurredUtc, recordedUtc, deviceId, eventId`. Public fields `List<SupplyEvent> events; List<String> errors;`.
  - `RetryResult retryPending()` — republishes leftover `pending/*.tmp`. Public fields `int recovered; List<String> errors;`.
  - `int pendingCount()`, `Path getSharedRoot()`, `void clearIndex()`.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/EventStoreTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EventStoreTest {
    public static void main(String[] args) throws Exception {
        Path shared = Files.createTempDirectory("medsupply-shared");
        Path localA = Files.createTempDirectory("medsupply-localA");
        Path localB = Files.createTempDirectory("medsupply-localB");

        EventStore a = new EventStore(shared, localA);
        SupplyEvent e1 = event("STOCK_RECEIVED", "WS-A", "2026-08-03T10:00:00Z", "5");
        SupplyEvent e2 = event("STOCK_PICKED", "WS-A", "2026-08-03T11:00:00Z", "-2");
        a.append(e1);
        a.append(e2);

        // A second workstation observes the same shared folder.
        EventStore b = new EventStore(shared, localB);
        EventStore.LoadResult loaded = b.loadAll();
        check(loaded.errors.isEmpty(), "no errors: " + loaded.errors);
        check(loaded.events.size() == 2, "two events, got " + loaded.events.size());
        check("STOCK_RECEIVED".equals(loaded.events.get(0).eventType), "sorted first");
        check("STOCK_PICKED".equals(loaded.events.get(1).eventType), "sorted second");
        check(a.pendingCount() == 0, "pending drained");
        System.out.println("EventStoreTest: PASS");
    }

    private static SupplyEvent event(String type, String device, String occurred, String delta) {
        SupplyEvent e = new SupplyEvent();
        e.eventType = type;
        e.deviceId = device;
        e.occurredUtc = occurred;
        e.recordedUtc = occurred;
        e.payload.put("gtin", "00380740000010");
        e.payload.put("quantityDelta", delta);
        return e;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... EventStore`.

- [ ] **Step 3: Write the implementation**

`medical-supply-java/src/main/java/org/medsupply/EventStore.java`:

```java
package org.medsupply;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class EventStore {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private final Path sharedRoot;
    private final Path pendingRoot;
    private final LocalEventIndex index;

    public EventStore(Path sharedRoot, Path localRoot) throws IOException {
        this.sharedRoot = sharedRoot;
        this.pendingRoot = localRoot.resolve("pending");
        this.index = new LocalEventIndex(localRoot);
        Files.createDirectories(sharedRoot.resolve("events"));
        Files.createDirectories(sharedRoot.resolve("reports"));
        Files.createDirectories(sharedRoot.resolve("configuration"));
        Files.createDirectories(sharedRoot.resolve("diagnostics"));
        Files.createDirectories(pendingRoot);
    }

    public synchronized Path append(SupplyEvent event) throws IOException {
        validate(event);
        String json = SupplyEventJson.write(event);
        Path local = pendingRoot.resolve(event.eventId + ".tmp");
        Files.write(local, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        force(local);
        Path published = finalizeShared(event, local);
        Files.deleteIfExists(local);
        return published;
    }

    private Path finalizeShared(SupplyEvent event, Path local) throws IOException {
        String safeDevice = event.deviceId.replaceAll("[^A-Za-z0-9-]", "_");
        String safeType = event.eventType.replaceAll("[^A-Za-z0-9_]", "_");
        String filename = FILE_TIME.format(Instant.parse(event.occurredUtc)) + "_" + safeDevice + "_"
                + event.eventId + "_" + safeType + ".json";
        Path month = sharedRoot.resolve("events")
                .resolve(event.occurredUtc.substring(0, 4))
                .resolve(event.occurredUtc.substring(5, 7));
        Files.createDirectories(month);
        Path partial = month.resolve(filename + ".partial");
        Path target = month.resolve(filename);
        Files.copy(local, partial, StandardCopyOption.REPLACE_EXISTING);
        force(partial);
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public synchronized RetryResult retryPending() {
        int recovered = 0;
        List<String> errors = new ArrayList<String>();
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(pendingRoot, "*.tmp")) {
            for (Path local : pending) {
                try {
                    if (Files.size(local) > 1024 * 1024) throw new IOException("Pending file exceeds 1 MB");
                    SupplyEvent event = SupplyEventJson.read(
                            new String(Files.readAllBytes(local), StandardCharsets.UTF_8));
                    validate(event);
                    finalizeShared(event, local);
                    Files.deleteIfExists(local);
                    recovered++;
                } catch (Exception ex) {
                    errors.add(local.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            errors.add("Pending scan failed: " + ex.getMessage());
        }
        return new RetryResult(recovered, errors);
    }

    public synchronized LoadResult loadAll() {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        List<String> errors = new ArrayList<String>();
        Set<String> ids = new HashSet<String>();
        Map<String, String> hashes = new HashMap<String, String>();
        Map<String, LocalEventIndex.Entry> currentIndex = new HashMap<String, LocalEventIndex.Entry>();
        Path eventsRoot = sharedRoot.resolve("events");
        if (!Files.isDirectory(eventsRoot)) return new LoadResult(events, errors);
        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            if (Files.size(path) > 1024 * 1024) throw new IOException("File exceeds 1 MB");
                            String relative = sharedRoot.relativize(path).toString();
                            long size = Files.size(path);
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            LocalEventIndex.Entry cached = index.find(relative, size, modified);
                            String json = cached == null
                                    ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : cached.json;
                            SupplyEvent event = SupplyEventJson.read(json);
                            validate(event);
                            String hash = sha256(SupplyEventJson.write(event).getBytes(StandardCharsets.UTF_8));
                            currentIndex.put(relative, new LocalEventIndex.Entry(relative, size, modified, hash, json));
                            if (ids.add(event.eventId)) {
                                hashes.put(event.eventId, hash);
                                events.add(event);
                            } else if (!hash.equals(hashes.get(event.eventId))) {
                                errors.add(path.getFileName() + ": duplicate event ID has different content");
                            }
                        } catch (Exception ex) {
                            errors.add(path.getFileName() + ": " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            errors.add("Event scan failed: " + ex.getMessage());
        }
        Collections.sort(events, Comparator.comparing((SupplyEvent e) -> e.occurredUtc)
                .thenComparing(e -> e.recordedUtc).thenComparing(e -> e.deviceId).thenComparing(e -> e.eventId));
        try { index.replace(currentIndex); } catch (IOException ex) {
            errors.add("Local index update failed: " + ex.getMessage());
        }
        return new LoadResult(events, errors);
    }

    public Path getSharedRoot() { return sharedRoot; }

    public int pendingCount() {
        int count = 0;
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(pendingRoot, "*.tmp")) {
            for (Path ignored : pending) count++;
        } catch (IOException ignored) { }
        return count;
    }

    public void clearIndex() throws IOException { index.clear(); }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte item : hash) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static void validate(SupplyEvent event) {
        if (!"1".equals(event.schemaVersion)) throw new IllegalArgumentException("Unsupported schema");
        if (!event.eventId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid event ID");
        if (event.eventType.length() == 0 || event.eventType.length() > 40)
            throw new IllegalArgumentException("Invalid event type");
        Instant.parse(event.occurredUtc);
    }

    public static final class LoadResult {
        public final List<SupplyEvent> events;
        public final List<String> errors;
        LoadResult(List<SupplyEvent> events, List<String> errors) {
            this.events = events;
            this.errors = errors;
        }
    }

    public static final class RetryResult {
        public final int recovered;
        public final List<String> errors;
        RetryResult(int recovered, List<String> errors) {
            this.recovered = recovered;
            this.errors = errors;
        }
    }
}
```

Note: `LinkedHashMap` is imported for parity with future edits; if `-Werror`-style unused-import checks are added later, remove it. It is not an error under stock `javac`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `EventStoreTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/EventStore.java medical-supply-java/src/test/java/org/medsupply/EventStoreTest.java
git commit -m "feat(medsupply): atomic append/replay event store"
```

---

### Task 6: Per-user application configuration

**Files:**
- Create: `medical-supply-java/src/main/java/org/medsupply/AppConfig.java`
- Test: `medical-supply-java/src/test/java/org/medsupply/AppConfigTest.java`

**Interfaces:**
- Consumes: `Json` (Task 2).
- Produces:
  - `AppConfig.load() -> AppConfig` — reads/creates `<%LOCALAPPDATA%|user.home|-Dmedsupply.localBase>/MedicalSupply/config/client.json`.
  - Public fields: `Path sharedRoot` (nullable), `final Path localRoot`, `String deviceId`, `String actor`, `boolean gudidEnabled` (default `true`), `String gudidEndpoint` (default `https://accessgudid.nlm.nih.gov/api/v3/devices/lookup.json`), `int reorderWindowDays` (90), `int reorderLeadDays` (7), `int reorderSafetyDays` (7), `int reorderCoverageDays` (28), `int staleDays` (30), `int scannerMinimumLength` (5).
  - `void save()`.

- [ ] **Step 1: Write the failing test**

`medical-supply-java/src/test/java/org/medsupply/AppConfigTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AppConfigTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-cfg");
        System.setProperty("medsupply.localBase", base.toString());

        AppConfig config = AppConfig.load();
        check(config.gudidEnabled, "gudid default on");
        check(config.reorderWindowDays == 90, "window default");
        check(config.staleDays == 30, "stale default");
        check(config.gudidEndpoint.startsWith("https://accessgudid"), "endpoint default");

        config.sharedRoot = base.resolve("shared");
        config.reorderWindowDays = 45;
        config.gudidEnabled = false;
        config.save();

        AppConfig reloaded = AppConfig.load();
        check(base.resolve("shared").equals(reloaded.sharedRoot), "shared persisted");
        check(reloaded.reorderWindowDays == 45, "window persisted");
        check(!reloaded.gudidEnabled, "gudid persisted");
        System.out.println("AppConfigTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: FAIL — `cannot find symbol ... AppConfig`.

- [ ] **Step 3: Write the implementation**

`medical-supply-java/src/main/java/org/medsupply/AppConfig.java`:

```java
package org.medsupply;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AppConfig {
    public Path sharedRoot;
    public final Path localRoot;
    public String deviceId;
    public String actor;
    public boolean gudidEnabled = true;
    public String gudidEndpoint = "https://accessgudid.nlm.nih.gov/api/v3/devices/lookup.json";
    public int reorderWindowDays = 90;
    public int reorderLeadDays = 7;
    public int reorderSafetyDays = 7;
    public int reorderCoverageDays = 28;
    public int staleDays = 30;
    public int scannerMinimumLength = 5;
    public String activeSessionId = "";

    private AppConfig(Path localRoot) { this.localRoot = localRoot; }

    public static AppConfig load() throws IOException {
        String base = System.getProperty("medsupply.localBase", "");
        if (base.trim().length() == 0) base = System.getenv("LOCALAPPDATA");
        if (base == null || base.trim().length() == 0) base = System.getProperty("user.home");
        Path local = Paths.get(base, "MedicalSupply");
        Files.createDirectories(local.resolve("config"));
        AppConfig config = new AppConfig(local);
        Path file = local.resolve("config").resolve("client.json");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (Files.isRegularFile(file))
            values = Json.asMap(Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));

        String root = Json.str(values, "sharedRoot");
        config.sharedRoot = root.length() == 0 ? null : Paths.get(root);
        config.deviceId = orDefault(Json.str(values, "deviceId"), defaultDevice());
        config.actor = orDefault(Json.str(values, "actor"), defaultActor());
        config.gudidEnabled = !"false".equals(Json.str(values, "gudidEnabled"));
        config.gudidEndpoint = orDefault(Json.str(values, "gudidEndpoint"), config.gudidEndpoint);
        config.reorderWindowDays = intOr(values, "reorderWindowDays", 90, 7, 365);
        config.reorderLeadDays = intOr(values, "reorderLeadDays", 7, 0, 120);
        config.reorderSafetyDays = intOr(values, "reorderSafetyDays", 7, 0, 120);
        config.reorderCoverageDays = intOr(values, "reorderCoverageDays", 28, 1, 365);
        config.staleDays = intOr(values, "staleDays", 30, 1, 365);
        config.scannerMinimumLength = intOr(values, "scannerMinimumLength", 5, 4, 100);
        config.activeSessionId = orDefault(Json.str(values, "activeSessionId"), UUID.randomUUID().toString());
        if (!Files.isRegularFile(file)) config.save();
        return config;
    }

    public void save() throws IOException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("sharedRoot", sharedRoot == null ? "" : sharedRoot.toString());
        values.put("deviceId", deviceId);
        values.put("actor", actor);
        values.put("gudidEnabled", gudidEnabled ? "true" : "false");
        values.put("gudidEndpoint", gudidEndpoint);
        values.put("reorderWindowDays", Integer.valueOf(reorderWindowDays));
        values.put("reorderLeadDays", Integer.valueOf(reorderLeadDays));
        values.put("reorderSafetyDays", Integer.valueOf(reorderSafetyDays));
        values.put("reorderCoverageDays", Integer.valueOf(reorderCoverageDays));
        values.put("staleDays", Integer.valueOf(staleDays));
        values.put("scannerMinimumLength", Integer.valueOf(scannerMinimumLength));
        values.put("activeSessionId", activeSessionId);
        Path configRoot = localRoot.resolve("config");
        Files.createDirectories(configRoot);
        Files.write(configRoot.resolve("client.json"), Json.write(values).getBytes(StandardCharsets.UTF_8));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static int intOr(Map<String, Object> values, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(Json.str(values, key));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ex) { return fallback; }
    }

    private static String defaultDevice() {
        String machine = System.getenv("COMPUTERNAME");
        if (machine == null || machine.length() == 0) machine = "DEVICE";
        return machine.toUpperCase().replaceAll("[^A-Z0-9-]", "-");
    }

    private static String defaultActor() {
        String user = System.getProperty("user.name", "unknown");
        String domain = System.getenv("USERDOMAIN");
        return domain == null || domain.trim().length() == 0 ? user : domain + "\\" + user;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `powershell -File medical-supply-java/build.ps1`
Expected: `AppConfigTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/AppConfig.java medical-supply-java/src/test/java/org/medsupply/AppConfigTest.java
git commit -m "feat(medsupply): per-user application configuration"
```

---

### Task 7: Wire the headless self-test end to end

**Files:**
- Modify: `medical-supply-java/src/main/java/org/medsupply/SelfTest.java`

**Interfaces:**
- Consumes: `AppConfig`, `EventStore`, `SupplyEvent` (Tasks 3, 5, 6).
- Produces: a `SelfTest.run()` that exercises config + append + reload against a temp folder, so `--self-test` proves the foundation works from the packaged JAR.

- [ ] **Step 1: Replace the trivial self test with an end-to-end smoke check**

`medical-supply-java/src/main/java/org/medsupply/SelfTest.java`:

```java
package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SelfTest {
    private SelfTest() {}

    public static void run() throws Exception {
        if (!"1".equals(SupplyMeta.SCHEMA_VERSION)) throw new AssertionError("Unexpected schema version");

        Path shared = Files.createTempDirectory("medsupply-selftest-shared");
        Path local = Files.createTempDirectory("medsupply-selftest-local");
        EventStore store = new EventStore(shared, local);

        SupplyEvent event = new SupplyEvent();
        event.eventType = "STOCK_RECEIVED";
        event.deviceId = "SELFTEST";
        event.occurredUtc = "2026-08-03T12:00:00Z";
        event.recordedUtc = "2026-08-03T12:00:00Z";
        event.payload.put("gtin", "00380740000010");
        event.payload.put("quantityDelta", "3");
        store.append(event);

        EventStore.LoadResult loaded = store.loadAll();
        if (!loaded.errors.isEmpty()) throw new AssertionError("Load errors: " + loaded.errors);
        if (loaded.events.size() != 1) throw new AssertionError("Expected 1 event, got " + loaded.events.size());
        if (!"3".equals(loaded.events.get(0).payload("quantityDelta")))
            throw new AssertionError("Payload roundtrip failed");
    }
}
```

- [ ] **Step 2: Build and run the packaged self-test**

Run:
```
powershell -File medical-supply-java/build.ps1
java -jar medical-supply-java/dist/MedicalSupply-RC.jar --self-test
```
Expected: all `*Test: PASS` lines during build, then `MedicalSupply self-test: PASS`.

- [ ] **Step 3: Commit**

```bash
git add medical-supply-java/src/main/java/org/medsupply/SelfTest.java
git commit -m "test(medsupply): end-to-end headless self-test"
```

---

## Self-Review

**Spec coverage (Plan 1 scope):**
- Project scaffold, Java 8 build, no Maven/Gradle → Task 1. ✓
- Nested JSON reader required by GUDID (§5) → Task 2 (`Json`, verified against a GUDID-shaped fixture). ✓
- Event envelope + payload (§3.2) → Task 3. ✓
- Local rebuildable index (§3.1) → Task 4. ✓
- Atomic append/replay/dedup/pending-retry event store (§3.1) → Task 5. ✓
- Per-user config incl. GUDID + reorder-heuristic parameters (§5, §6.2, §7 Settings) → Task 6. ✓
- `--self-test` entry point (§8) → Tasks 1 + 7. ✓
- Deferred to later plans (out of scope here, by design): event-type semantics & `itemKey` (Plan 2), `Gs1Parser` (Plan 2), `GudidClient` (Plan 2), projections/analytics (Plan 2), UI/labels/report (Plan 3), `SharedConfigManager` shared settings, frontend npm in `build.ps1`, README/TESTING/RELEASE_NOTES/qualification packaging (Plan 3).

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"similar to Task N". Every code step contains complete, compilable code. ✓

**Type consistency:** `Json.str`/`asMap`/`asList`/`parse`/`write` signatures are used identically in Tasks 3–6. `SupplyEvent.payload` (a `Map<String,String>`) and `payload(String)` match across Tasks 3, 5, 7. `LocalEventIndex.Entry(relativePath,size,modifiedMillis,fileHash,json)` constructor matches its use in Task 5. `EventStore.LoadResult.events/errors` and `RetryResult.recovered/errors` match the test in Task 5 and the self-test in Task 7. ✓

## Execution Handoff

This is Plan 1 of 3. Plans 2 (domain & analytics) and 3 (UI & output) will be written after this foundation is approved/executed, since their tasks reference concrete types produced here.
