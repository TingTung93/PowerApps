package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalEventIndex {
    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    public LocalEventIndex(Path localRoot) throws IOException {
        Path cache = localRoot.resolve("cache");
        Files.createDirectories(cache);
        file = cache.resolve("event-index.tsv");
        load();
    }

    public synchronized Entry find(String path, long size, long modified) {
        Entry entry = entries.get(path);
        return entry != null && entry.size == size && entry.modified == modified ? entry : null;
    }

    public synchronized void replace(Map<String, Entry> current) throws IOException {
        entries.clear();
        entries.putAll(current);
        List<String> lines = new ArrayList<String>();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (Entry entry : entries.values()) {
            lines.add(encoder.encodeToString(entry.path.getBytes(StandardCharsets.UTF_8)) + "\t"
                    + entry.size + "\t" + entry.modified + "\t" + entry.checksum + "\t"
                    + encoder.encodeToString(entry.json.getBytes(StandardCharsets.UTF_8)));
        }
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        Files.write(partial, lines, StandardCharsets.UTF_8);
        try { Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ex) { Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    public synchronized void clear() throws IOException {
        entries.clear();
        Files.deleteIfExists(file);
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() > 250000) throw new IOException("Index exceeds safe entry limit.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            for (String line : lines) {
                String[] fields = line.split("\\t", 5);
                if (fields.length != 5) continue;
                String path = new String(decoder.decode(fields[0]), StandardCharsets.UTF_8);
                String json = new String(decoder.decode(fields[4]), StandardCharsets.UTF_8);
                entries.put(path, new Entry(path, Long.parseLong(fields[1]), Long.parseLong(fields[2]), fields[3], json));
            }
        } catch (Exception ex) {
            entries.clear();
            try {
                Files.move(file, file.resolveSibling("event-index.corrupt-" + System.currentTimeMillis() + ".tsv"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
    }

    public static final class Entry {
        public final String path;
        public final long size;
        public final long modified;
        public final String checksum;
        public final String json;
        public Entry(String path, long size, long modified, String checksum, String json) {
            this.path = path; this.size = size; this.modified = modified; this.checksum = checksum; this.json = json;
        }
    }
}
