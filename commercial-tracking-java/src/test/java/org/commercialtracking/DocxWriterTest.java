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
