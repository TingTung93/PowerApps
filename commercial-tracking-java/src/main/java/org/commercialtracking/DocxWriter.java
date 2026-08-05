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

    String relationships() {
        StringBuilder r = new StringBuilder();
        for (int i = 1; i <= media.size(); i++)
            r.append("<Relationship Id=\"rIdImg").append(i)
             .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image")
             .append(i).append(".png\"/>");
        return r.toString();
    }

    void writeMedia(ZipOutputStream zip) throws IOException {
        for (int i = 0; i < media.size(); i++)
            writeBytes(zip, "word/media/image" + (i + 1) + ".png", media.get(i));
    }

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
