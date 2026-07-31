package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PortablePdfTest {
    public static void main(String[] args) throws Exception {
        Path output = Files.createTempFile("commercial-pdf-", ".pdf");
        List<String> lines = new ArrayList<String>();
        lines.add("Tracking (test) \\ value");
        for (int i = 0; i < 97; i++) lines.add("Package " + i);
        PortablePdf.write(output, "Qualification — PDF", lines);
        byte[] bytes = Files.readAllBytes(output);
        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        check(pdf.startsWith("%PDF-1.4"), "PDF header");
        check(pdf.endsWith("%%EOF\n"), "PDF trailer");
        check(pdf.contains("/Count 3"), "pagination");
        check(pdf.contains("Tracking \\(test\\) \\\\ value"), "PDF string escaping");
        check(pdf.contains("Page 3 of 3"), "page footer");
        check(bytes.length > 1000, "non-empty PDF");
        System.out.println("PortablePdfTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
