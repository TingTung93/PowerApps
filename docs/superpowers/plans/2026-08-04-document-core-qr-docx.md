# Document Core (QR encoder + DOCX writer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the two dependency-free, JDK-only building blocks — a QR-code encoder (with PNG rendering) and a minimal DOCX writer — that later manifest and report plans consume.

**Architecture:** Two new self-contained Java classes in `org.commercialtracking` with no third-party dependencies. `QrCode` encodes a string into a QR module matrix (byte mode, ECC level M, versions 1–3) and can render it to PNG via `javax.imageio.ImageIO`. `DocxWriter` builds a valid OOXML `.docx` package (headings, paragraphs, tables, inline PNG images) using only `java.util.zip`. Each ships with a `main()`-style test class wired into `build.ps1`.

**Tech Stack:** Java 8 (`javac --release 8`), pure JDK (`java.util.zip`, `java.awt.image`, `javax.imageio`). No Maven/Gradle, no external jars. Tests are plain `main()` classes run by `build.ps1`.

## Global Constraints

- Pure JDK only — no third-party libraries may be added. Build is `javac --release 8 -encoding UTF-8`.
- New test classes are `public final class XxxTest { public static void main(String[] args) throws Exception { ... System.out.println("XxxTest: PASS"); } }` and must be added to `build.ps1` in the test-run block.
- Package is `org.commercialtracking`; source under `apps/commercial-tracking-java/src/main/java/org/commercialtracking/`, tests under `apps/commercial-tracking-java/src/test/java/org/commercialtracking/`.
- Build/test command (run from `apps/commercial-tracking-java/`): `powershell -File build.ps1 -SkipFrontend`.
- All paths below are relative to the repo root `F:\PowerApps`.

---

### Task 1: QrCode — Galois field + generator polynomial

**Files:**
- Create: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/QrCode.java`
- Create: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/QrCodeTest.java`

**Interfaces:**
- Produces: `class QrCode` with `public final int size;`, `public final boolean[][] modules;` (indexed `[row][col]`, `true` = dark), `static int[] generatorPolynomial(int degree)` (package-private, returns α-exponent coefficients, length `degree+1`, leading term first). Later steps add `public static QrCode encode(String text)` and `public byte[] toPng(int scale, int quietModules)`.

- [ ] **Step 1: Write the failing test**

Create `QrCodeTest.java`:

```java
package org.commercialtracking;

import java.util.Arrays;

public final class QrCodeTest {
    public static void main(String[] args) throws Exception {
        // Reed–Solomon generator polynomial for 10 ECC codewords, from the QR spec (Annex A),
        // expressed as alpha exponents, leading coefficient first.
        int[] expected10 = {0, 251, 67, 46, 61, 118, 70, 64, 94, 32, 45};
        int[] actual10 = QrCode.generatorPolynomial(10);
        check(Arrays.equals(expected10, actual10),
                "generator(10) = " + Arrays.toString(actual10));
        System.out.println("QrCodeTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File build.ps1 -SkipFrontend` (from `apps/commercial-tracking-java/`), after adding the `QrCodeTest` run line (Step 4).
Expected: compilation fails — `QrCode` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `QrCode.java`:

```java
package org.commercialtracking;

public final class QrCode {
    public final int size;
    public final boolean[][] modules; // [row][col], true = dark

    QrCode(int size, boolean[][] modules) {
        this.size = size;
        this.modules = modules;
    }

    // ---- Galois field GF(256), primitive polynomial 0x11D, generator element 2 ----
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];
    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    /** Generator polynomial coefficients as alpha exponents, length degree+1, leading term first. */
    static int[] generatorPolynomial(int degree) {
        int[] coeff = new int[degree + 1]; // coefficients as field values
        coeff[0] = 1;
        int len = 1;
        for (int i = 0; i < degree; i++) {
            len++;
            for (int j = len - 1; j > 0; j--)
                coeff[j] = coeff[j - 1] ^ mul(coeff[j], EXP[i]);
            coeff[0] = mul(coeff[0], EXP[i]);
        }
        int[] exps = new int[degree + 1];
        for (int i = 0; i <= degree; i++) exps[i] = LOG[coeff[i]];
        return exps;
    }
}
```

- [ ] **Step 4: Wire the test into build.ps1**

In `apps/commercial-tracking-java/build.ps1`, add after the `PortablePdfTest` block (around line 83), before the `PerformanceSmokeTest` block:

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.QrCodeTest
    if ($LASTEXITCODE -ne 0) { throw "QR code tests failed." }
```

- [ ] **Step 5: Run to verify it passes**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: output includes `QrCodeTest: PASS`; build succeeds.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/QrCode.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/QrCodeTest.java \
        apps/commercial-tracking-java/build.ps1
git commit -m "feat(qr): GF(256) field and Reed-Solomon generator polynomial"
```

---

### Task 2: QrCode — full encode to module matrix

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/QrCode.java`
- Modify: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/QrCodeTest.java`

**Interfaces:**
- Consumes: `generatorPolynomial`, `mul`, `EXP`, `LOG` from Task 1.
- Produces: `public static QrCode encode(String text)` — byte mode, ECC level M, versions 1–3, auto version + lowest-penalty mask; throws `IllegalArgumentException` when the payload exceeds version-3 capacity (~41 bytes).

- [ ] **Step 1: Write the failing test**

Append to `QrCodeTest.main` before the `PASS` line:

```java
        QrCode small = QrCode.encode("TRACKING-0001");            // 13 bytes -> version 1
        check(small.size == 21, "v1 size = " + small.size);
        check(finder(small, 0, 0), "top-left finder");
        check(finder(small, 0, small.size - 7), "top-right finder");
        check(finder(small, small.size - 7, 0), "bottom-left finder");

        QrCode mid = QrCode.encode("1Z999AA10123456784-EXTRA-PAYLOAD-XYZ"); // ~36 bytes -> version 3
        check(mid.size == 29, "v3 size = " + mid.size);

        boolean threw = false;
        try { QrCode.encode(repeat("X", 60)); } catch (IllegalArgumentException ex) { threw = true; }
        check(threw, "over-length payload rejected");
```

And add these helper methods to `QrCodeTest`:

```java
    // A finder pattern: dark 7x7 border+center at (r,c). Sample the four ring corners and center.
    private static boolean finder(QrCode q, int r, int c) {
        return q.modules[r][c] && q.modules[r][c + 6] && q.modules[r + 6][c]
                && q.modules[r + 6][c + 6] && q.modules[r + 3][c + 3]
                && !q.modules[r + 1][c + 1] && !q.modules[r + 1][c + 5];
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: compile error — `encode` is undefined.

- [ ] **Step 3: Write minimal implementation**

Add to `QrCode.java` (inside the class):

```java
    // ECC level M, versions 1..3 (single ECC block).
    private static final int[] VERSION_SIZE   = {0, 21, 25, 29};
    private static final int[] DATA_CODEWORDS = {0, 16, 28, 44};
    private static final int[] ECC_CODEWORDS  = {0, 10, 16, 26};
    private static final int[] ALIGN_CENTER   = {0, 0, 18, 22}; // 0 = no alignment pattern

    public static QrCode encode(String text) {
        byte[] data;
        try { data = text.getBytes("ISO-8859-1"); }
        catch (java.io.UnsupportedEncodingException ex) { throw new IllegalStateException(ex); }
        int version = chooseVersion(data.length);
        int[] codewords = buildCodewords(data, version);
        int n = VERSION_SIZE[version];
        boolean[][] m = new boolean[n][n];
        boolean[][] fn = new boolean[n][n];
        drawFunctionPatterns(version, m, fn);
        drawData(codewordBits(codewords), m, fn);
        int mask = chooseMask(version, m, fn);
        applyMask(mask, m, fn);
        drawFormat(mask, m);
        return new QrCode(n, m);
    }

    private static int chooseVersion(int dataLen) {
        for (int v = 1; v <= 3; v++)
            if (dataLen <= DATA_CODEWORDS[v] - 2) return v;
        throw new IllegalArgumentException("Payload too long for QR version 3: " + dataLen + " bytes");
    }

    private static int[] buildCodewords(byte[] data, int version) {
        int totalData = DATA_CODEWORDS[version];
        boolean[] bits = new boolean[totalData * 8];
        int p = 0;
        p = put(bits, p, 0b0100, 4);            // byte mode
        p = put(bits, p, data.length, 8);       // character count (8 bits, versions 1..9)
        for (byte b : data) p = put(bits, p, b & 0xFF, 8);
        // terminator: up to 4 zero bits, bounded by capacity
        for (int i = 0; i < 4 && p < bits.length; i++) p++;
        // pad to byte boundary (bits already default false)
        while (p % 8 != 0) p++;
        // pad bytes
        int[] out = new int[totalData + ECC_CODEWORDS[version]];
        int bytes = p / 8;
        for (int i = 0; i < bytes; i++) out[i] = bitsToByte(bits, i * 8);
        boolean ec11 = false;
        for (int i = bytes; i < totalData; i++) { out[i] = ec11 ? 0x11 : 0xEC; ec11 = !ec11; }
        int[] ecc = rsRemainder(java.util.Arrays.copyOf(out, totalData), ECC_CODEWORDS[version]);
        System.arraycopy(ecc, 0, out, totalData, ecc.length);
        return out;
    }

    private static int put(boolean[] bits, int p, int value, int len) {
        for (int i = len - 1; i >= 0; i--) bits[p++] = ((value >> i) & 1) != 0;
        return p;
    }

    private static int bitsToByte(boolean[] bits, int off) {
        int v = 0;
        for (int i = 0; i < 8; i++) v = (v << 1) | (bits[off + i] ? 1 : 0);
        return v;
    }

    private static int[] rsRemainder(int[] data, int eccLen) {
        int[] gen = generatorPolynomial(eccLen); // exponents, length eccLen+1
        int[] rem = new int[eccLen];
        for (int i = 0; i < data.length; i++) {
            int factor = data[i] ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, eccLen - 1);
            rem[eccLen - 1] = 0;
            if (factor != 0) {
                int lf = LOG[factor];
                for (int j = 0; j < eccLen; j++) rem[j] ^= EXP[lf + gen[j + 1]];
            }
        }
        return rem;
    }

    private static boolean[] codewordBits(int[] codewords) {
        boolean[] bits = new boolean[codewords.length * 8];
        int p = 0;
        for (int cw : codewords) p = put(bits, p, cw, 8);
        return bits;
    }

    // ---- matrix construction ----
    private static void set(boolean[][] m, int r, int c, boolean v) { m[r][c] = v; }

    private static void setFn(boolean[][] m, boolean[][] fn, int r, int c, boolean v) {
        if (r < 0 || c < 0 || r >= m.length || c >= m.length) return;
        m[r][c] = v; fn[r][c] = true;
    }

    private static void drawFunctionPatterns(int version, boolean[][] m, boolean[][] fn) {
        int n = m.length;
        drawFinder(m, fn, 0, 0);
        drawFinder(m, fn, 0, n - 7);
        drawFinder(m, fn, n - 7, 0);
        for (int i = 8; i < n - 8; i++) {
            boolean dark = (i % 2 == 0);
            setFn(m, fn, 6, i, dark);
            setFn(m, fn, i, 6, dark);
        }
        setFn(m, fn, n - 8, 8, true); // dark module
        int c = ALIGN_CENTER[version];
        if (c != 0)
            for (int dr = -2; dr <= 2; dr++)
                for (int dc = -2; dc <= 2; dc++)
                    setFn(m, fn, c + dr, c + dc, Math.max(Math.abs(dr), Math.abs(dc)) != 1);
        // reserve format-info modules (values assigned later); mark as function so data skips them
        for (int i = 0; i <= 8; i++) { reserve(fn, 8, i); reserve(fn, i, 8); }
        for (int i = 0; i < 8; i++) { reserve(fn, 8, n - 1 - i); reserve(fn, n - 1 - i, 8); }
    }

    private static void reserve(boolean[][] fn, int r, int c) {
        if (r >= 0 && c >= 0 && r < fn.length && c < fn.length) fn[r][c] = true;
    }

    private static void drawFinder(boolean[][] m, boolean[][] fn, int row, int col) {
        for (int r = -1; r <= 7; r++)
            for (int c = -1; c <= 7; c++) {
                boolean dark = (r >= 0 && r <= 6 && c >= 0 && c <= 6)
                        && (r == 0 || r == 6 || c == 0 || c == 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
                setFn(m, fn, row + r, col + c, dark);
            }
    }

    private static void drawData(boolean[] bits, boolean[][] m, boolean[][] fn) {
        int n = m.length, bit = 0;
        boolean upward = true;
        for (int col = n - 1; col > 0; col -= 2) {
            if (col == 6) col = 5; // skip the vertical timing column
            for (int i = 0; i < n; i++) {
                int row = upward ? n - 1 - i : i;
                for (int c = 0; c < 2; c++) {
                    int cc = col - c;
                    if (!fn[row][cc]) {
                        boolean dark = bit < bits.length && bits[bit];
                        bit++;
                        m[row][cc] = dark;
                    }
                }
            }
            upward = !upward;
        }
    }

    private static boolean maskCondition(int mask, int r, int c) {
        switch (mask) {
            case 0: return (r + c) % 2 == 0;
            case 1: return r % 2 == 0;
            case 2: return c % 3 == 0;
            case 3: return (r + c) % 3 == 0;
            case 4: return (r / 2 + c / 3) % 2 == 0;
            case 5: return (r * c) % 2 + (r * c) % 3 == 0;
            case 6: return ((r * c) % 2 + (r * c) % 3) % 2 == 0;
            default: return ((r + c) % 2 + (r * c) % 3) % 2 == 0;
        }
    }

    private static void applyMask(int mask, boolean[][] m, boolean[][] fn) {
        for (int r = 0; r < m.length; r++)
            for (int c = 0; c < m.length; c++)
                if (!fn[r][c] && maskCondition(mask, r, c)) m[r][c] = !m[r][c];
    }

    private static int chooseMask(int version, boolean[][] m, boolean[][] fn) {
        int best = 0, bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] t = new boolean[m.length][];
            for (int r = 0; r < m.length; r++) t[r] = m[r].clone();
            applyMask(mask, t, fn);
            drawFormat(mask, t);
            int p = penalty(t);
            if (p < bestPenalty) { bestPenalty = p; best = mask; }
        }
        return best;
    }

    private static int penalty(boolean[][] m) {
        int n = m.length, score = 0;
        // Rule 1: runs of >=5 in rows and columns
        for (int r = 0; r < n; r++) { score += runPenalty(m[r]); }
        for (int c = 0; c < n; c++) {
            boolean[] col = new boolean[n];
            for (int r = 0; r < n; r++) col[r] = m[r][c];
            score += runPenalty(col);
        }
        // Rule 2: 2x2 blocks
        for (int r = 0; r < n - 1; r++)
            for (int c = 0; c < n - 1; c++)
                if (m[r][c] == m[r][c + 1] && m[r][c] == m[r + 1][c] && m[r][c] == m[r + 1][c + 1])
                    score += 3;
        // Rule 3: finder-like 1:1:3:1:1 patterns (with 4 light either side)
        boolean[] a = {true, false, true, true, true, false, true, false, false, false, false};
        boolean[] b = {false, false, false, false, true, false, true, true, true, false, true};
        for (int r = 0; r < n; r++)
            for (int c = 0; c <= n - 11; c++) {
                if (matches(m, r, c, a, true) || matches(m, r, c, b, true)) score += 40;
            }
        for (int c = 0; c < n; c++)
            for (int r = 0; r <= n - 11; r++) {
                if (matches(m, r, c, a, false) || matches(m, r, c, b, false)) score += 40;
            }
        // Rule 4: dark proportion
        int dark = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (m[r][c]) dark++;
        int percent = dark * 100 / (n * n);
        score += Math.min(Math.abs(percent - 50) / 5 * 10, 100);
        return score;
    }

    private static int runPenalty(boolean[] line) {
        int score = 0, run = 1;
        for (int i = 1; i < line.length; i++) {
            if (line[i] == line[i - 1]) { run++; }
            else { if (run >= 5) score += 3 + (run - 5); run = 1; }
        }
        if (run >= 5) score += 3 + (run - 5);
        return score;
    }

    private static boolean matches(boolean[][] m, int r, int c, boolean[] pattern, boolean horizontal) {
        for (int i = 0; i < pattern.length; i++) {
            boolean v = horizontal ? m[r][c + i] : m[r + i][c];
            if (v != pattern[i]) return false;
        }
        return true;
    }

    private static int formatBits(int mask) {
        int data = (0b00 << 3) | mask; // EC level M = 00
        int dividend = data << 10;
        for (int i = 14; i >= 10; i--)
            if (((dividend >> i) & 1) == 1) dividend ^= (0x537 << (i - 10));
        return ((data << 10) | (dividend & 0x3FF)) ^ 0x5412;
    }

    private static void drawFormat(int mask, boolean[][] m) {
        int n = m.length, bits = formatBits(mask);
        for (int i = 0; i < 15; i++) {
            boolean v = ((bits >> i) & 1) != 0;
            // copy 1 (around top-left finder)
            if (i < 6) set(m, 8, i, v);
            else if (i < 8) set(m, 8, i + 1, v);
            else if (i == 8) set(m, 7, 8, v);
            else set(m, 14 - i, 8, v);
            // copy 2 (split across top-right and bottom-left)
            if (i < 8) set(m, 8, n - 1 - i, v);
            else set(m, n - 15 + i, 8, v);
        }
        m[n - 8][8] = true; // dark module (re-assert)
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: `QrCodeTest: PASS`.

- [ ] **Step 5: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/QrCode.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/QrCodeTest.java
git commit -m "feat(qr): encode byte-mode QR (versions 1-3, ECC-M) with mask selection"
```

---

### Task 3: QrCode — PNG rendering

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/QrCode.java`
- Modify: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/QrCodeTest.java`

**Interfaces:**
- Produces: `public byte[] toPng(int scale, int quietModules) throws java.io.IOException` — a PNG whose pixel side is `(size + quietModules*2) * scale`, dark modules black on white.

- [ ] **Step 1: Write the failing test**

Append to `QrCodeTest.main` before the `PASS` line:

```java
        byte[] png = QrCode.encode("TRACKING-0001").toPng(4, 2);
        check(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G',
                "PNG magic header");
        java.awt.image.BufferedImage img =
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        check(img.getWidth() == (21 + 2 * 2) * 4, "png width = " + img.getWidth());
        check(img.getHeight() == img.getWidth(), "png square");
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: compile error — `toPng` undefined.

- [ ] **Step 3: Write minimal implementation**

Add to `QrCode.java`:

```java
    public byte[] toPng(int scale, int quietModules) throws java.io.IOException {
        int dim = (size + quietModules * 2) * scale;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(dim, dim, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, dim, dim);
        g.setColor(java.awt.Color.BLACK);
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (modules[r][c])
                    g.fillRect((c + quietModules) * scale, (r + quietModules) * scale, scale, scale);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: `QrCodeTest: PASS`.

- [ ] **Step 5: Manual scan check**

Run this one-off snippet to write a QR PNG, then scan it with a phone QR reader; it must decode to `1Z999AA10123456784`:

```bash
cd apps/commercial-tracking-java
cat > /tmp/QrManual.java <<'EOF'
import org.commercialtracking.QrCode;
import java.nio.file.*;
public class QrManual {
  public static void main(String[] a) throws Exception {
    Files.write(Paths.get("qr-manual.png"), QrCode.encode("1Z999AA10123456784").toPng(8, 4));
  }
}
EOF
javac --release 8 -cp build/classes -d /tmp /tmp/QrManual.java
java -cp "build/classes;/tmp" QrManual
```

Expected: `qr-manual.png` scans to `1Z999AA10123456784`. (If it does not decode, the mask/format/data-placement logic in Task 2 needs debugging before proceeding — the unit tests do not prove scannability.) Delete `qr-manual.png` afterward.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/QrCode.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/QrCodeTest.java
git commit -m "feat(qr): render QR matrix to PNG via ImageIO"
```

---

### Task 4: DocxWriter — package skeleton, headings, paragraphs

**Files:**
- Create: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/DocxWriter.java`
- Create: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/DocxWriterTest.java`

**Interfaces:**
- Produces: `class DocxWriter` with fluent `heading(String)`, `paragraph(String)`, and `void save(Path)`. Task 5 adds tables and inline images.

- [ ] **Step 1: Write the failing test**

Create `DocxWriterTest.java`:

```java
package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DocxWriterTest {
    public static void main(String[] args) throws Exception {
        Path out = Files.createTempFile("commercial-docx-", ".docx");
        new DocxWriter()
                .heading("Inbound Receiving Manifest")
                .paragraph("Manifest ID: MNF-20260804-ABCDEF & <ok>")
                .save(out);
        try (ZipFile zip = new ZipFile(out.toFile())) {
            check(zip.getEntry("[Content_Types].xml") != null, "content types part");
            check(zip.getEntry("_rels/.rels") != null, "root rels part");
            check(zip.getEntry("word/document.xml") != null, "document part");
            ZipEntry doc = zip.getEntry("word/document.xml");
            String xml = new String(readAll(zip, doc), StandardCharsets.UTF_8);
            check(xml.contains("Inbound Receiving Manifest"), "heading text present");
            check(xml.contains("MNF-20260804-ABCDEF &amp; &lt;ok&gt;"), "paragraph text XML-escaped");
        }
        System.out.println("DocxWriterTest: PASS");
    }

    private static byte[] readAll(ZipFile zip, ZipEntry e) throws Exception {
        java.io.InputStream in = zip.getInputStream(e);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        return out.toByteArray();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File build.ps1 -SkipFrontend` (after adding the `DocxWriterTest` run line in Step 4).
Expected: compile error — `DocxWriter` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `DocxWriter.java`:

```java
package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DocxWriter {
    private final StringBuilder body = new StringBuilder();

    public DocxWriter heading(String text) {
        body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")
            .append(run(text)).append("</w:p>");
        return this;
    }

    public DocxWriter paragraph(String text) {
        body.append("<w:p>").append(run(text)).append("</w:p>");
        return this;
    }

    static String run(String text) {
        return "<w:r><w:t xml:space=\"preserve\">" + xml(text) + "</w:t></w:r>";
    }

    static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public void save(Path out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            write(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>");
            write(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>");
            write(zip, "word/_rels/document.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + relationships() + "</Relationships>");
            write(zip, "word/document.xml", document());
            writeMedia(zip);
        }
    }

    // Replaced in Task 5 with image-aware bodies; empty here so Task 4 compiles and runs on its own.
    String relationships() { return ""; }
    void writeMedia(ZipOutputStream zip) throws IOException { }

    private String document() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document "
            + "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
            + "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" "
            + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
            + "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<w:body>" + body + "<w:sectPr/></w:body></w:document>";
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static void writeBytes(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
```

- [ ] **Step 4: Wire the test into build.ps1**

In `apps/commercial-tracking-java/build.ps1`, add after the `QrCodeTest` block:

```powershell
    & java -cp "$classes;$testClasses" org.commercialtracking.DocxWriterTest
    if ($LASTEXITCODE -ne 0) { throw "DOCX writer tests failed." }
```

- [ ] **Step 5: Run to verify it passes**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: `DocxWriterTest: PASS`.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/DocxWriter.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/DocxWriterTest.java \
        apps/commercial-tracking-java/build.ps1
git commit -m "feat(docx): minimal OOXML writer with headings and paragraphs"
```

---

### Task 5: DocxWriter — tables and inline PNG images

**Files:**
- Modify: `apps/commercial-tracking-java/src/main/java/org/commercialtracking/DocxWriter.java`
- Modify: `apps/commercial-tracking-java/src/test/java/org/commercialtracking/DocxWriterTest.java`

**Interfaces:**
- Consumes: `DocxWriter` base (Task 4), `QrCode.toPng` (Task 3).
- Produces: `DocxWriter.Cell` with `static Cell text(String)` and `static Cell image(byte[] png, int widthPx, int heightPx)`; `DocxWriter table(java.util.List<java.util.List<Cell>> rows)`. Each image adds a `word/media/imageN.png` part and a matching relationship.

- [ ] **Step 1: Write the failing test**

Append to `DocxWriterTest.main` before the `PASS` line:

```java
        Path withImage = Files.createTempFile("commercial-docx-img-", ".docx");
        byte[] png = QrCode.encode("1Z999AA10123456784").toPng(4, 2);
        java.util.List<DocxWriter.Cell> row = new java.util.ArrayList<DocxWriter.Cell>();
        row.add(DocxWriter.Cell.text("1Z999AA10123456784"));
        row.add(DocxWriter.Cell.image(png, 96, 96));
        java.util.List<java.util.List<DocxWriter.Cell>> rows = new java.util.ArrayList<java.util.List<DocxWriter.Cell>>();
        rows.add(row);
        new DocxWriter().heading("Grid").table(rows).save(withImage);
        try (ZipFile zip = new ZipFile(withImage.toFile())) {
            check(zip.getEntry("word/media/image1.png") != null, "image media part");
            String rels = new String(readAll(zip, zip.getEntry("word/_rels/document.xml.rels")), StandardCharsets.UTF_8);
            check(rels.contains("media/image1.png"), "image relationship");
            String xml = new String(readAll(zip, zip.getEntry("word/document.xml")), StandardCharsets.UTF_8);
            check(xml.contains("<w:tbl>"), "table element");
            check(xml.contains("<w:drawing>"), "inline drawing");
        }
```

- [ ] **Step 2: Run to verify it fails**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: compile error — `DocxWriter.Cell` / `table` undefined.

- [ ] **Step 3: Write minimal implementation**

Add the `media` field, `Cell`, `table`, and `imageParagraph` members below to `DocxWriter.java`, and replace the **bodies** of the two Task-4 methods `relationships()` and `writeMedia(ZipOutputStream)` with the image-aware versions shown at the end of this block (do not add second copies — `DocxWriter` is `final`, so these are plain method-body edits, not overrides):

```java
    private final java.util.List<byte[]> media = new java.util.ArrayList<byte[]>();

    public static final class Cell {
        final String text; final byte[] png; final int w, h;
        private Cell(String text, byte[] png, int w, int h) { this.text = text; this.png = png; this.w = w; this.h = h; }
        public static Cell text(String t) { return new Cell(t, null, 0, 0); }
        public static Cell image(byte[] png, int widthPx, int heightPx) { return new Cell(null, png, widthPx, heightPx); }
    }

    public DocxWriter table(java.util.List<java.util.List<Cell>> rows) {
        body.append("<w:tbl><w:tblPr><w:tblBorders>")
            .append("<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
            .append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
            .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
            .append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
            .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
            .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
            .append("</w:tblBorders></w:tblPr>");
        for (java.util.List<Cell> row : rows) {
            body.append("<w:tr>");
            for (Cell cell : row) {
                body.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>");
                if (cell.png != null) body.append(imageParagraph(cell));
                else body.append("<w:p>").append(run(cell.text == null ? "" : cell.text)).append("</w:p>");
                body.append("</w:tc>");
            }
            body.append("</w:tr>");
        }
        body.append("</w:tbl>");
        return this;
    }

    private String imageParagraph(Cell cell) {
        media.add(cell.png);
        int id = media.size();
        long cx = cell.w * 9525L, cy = cell.h * 9525L; // EMU per pixel at 96 DPI
        String rid = "rIdImg" + id;
        return "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
            + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
            + "<wp:docPr id=\"" + id + "\" name=\"img" + id + "\"/>"
            + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"" + id + "\" name=\"img" + id + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
            + "<pic:blipFill><a:blip r:embed=\"" + rid + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
            + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
            + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
    }

    // --- replace the Task-4 body of relationships() with this ---
    String relationships() {
        StringBuilder r = new StringBuilder();
        for (int i = 1; i <= media.size(); i++)
            r.append("<Relationship Id=\"rIdImg").append(i)
             .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image")
             .append(i).append(".png\"/>");
        return r.toString();
    }

    // --- replace the Task-4 body of writeMedia() with this ---
    void writeMedia(ZipOutputStream zip) throws IOException {
        for (int i = 0; i < media.size(); i++)
            writeBytes(zip, "word/media/image" + (i + 1) + ".png", media.get(i));
    }
```

These two methods keep the same signatures they had in Task 4; only their bodies change. There is exactly one copy of each in the final file.

- [ ] **Step 4: Run to verify it passes**

Run: `powershell -File build.ps1 -SkipFrontend`
Expected: `DocxWriterTest: PASS`.

- [ ] **Step 5: Manual Word check**

Open the temp `.docx` (path printed by adding a temporary `System.out.println(withImage)` if needed) in Microsoft Word; confirm the heading, the table, and the embedded QR image render and the QR scans. Remove any temporary print line afterward.

- [ ] **Step 6: Commit**

```bash
git add apps/commercial-tracking-java/src/main/java/org/commercialtracking/DocxWriter.java \
        apps/commercial-tracking-java/src/test/java/org/commercialtracking/DocxWriterTest.java
git commit -m "feat(docx): tables and inline PNG images"
```

---

## Self-Review

**Spec coverage (Plan 1 scope = spec §1 QR encoder, §2 DOCX generator):**
- §1 byte mode / ECC-M / versions 1–3 / auto version / mask selection / `toPng` → Tasks 1–3. ✓
- §1 tests: generator-poly vector, size, finder patterns, over-length throw, PNG → Tasks 1–3. ✓
- §2 DOCX parts, headings/paragraphs/table/image, XML escaping, media part + relationship → Tasks 4–5. ✓
- §2 test: valid ZIP, required parts, escaped text, image media part + relationship → Tasks 4–5. ✓
- build.ps1 wiring for both new test classes → Tasks 1 & 4. ✓

**Placeholder scan:** No "TBD"/"handle errors"/"similar to". Task 5 explicitly resolves the Task-4 placeholder-method note by replacing them with concrete definitions. ✓

**Type consistency:** `QrCode.encode`/`toPng`/`generatorPolynomial`, `DocxWriter.heading`/`paragraph`/`table`/`save`, `DocxWriter.Cell.text`/`image` are used identically in tests and implementations. Relationship IDs (`rIdImg{n}`) match between `imageParagraph` and `relationships`. ✓

**Note carried to Plan 2/3:** `ManifestWriter`/`ReportWriter` will consume `DocxWriter` + `QrCode`; the checksum in `ManifestWriter.Result` must be computed over the `.docx` bytes.
