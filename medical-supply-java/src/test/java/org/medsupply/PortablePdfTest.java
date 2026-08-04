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
