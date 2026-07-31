package org.commercialtracking;

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
