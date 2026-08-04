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
