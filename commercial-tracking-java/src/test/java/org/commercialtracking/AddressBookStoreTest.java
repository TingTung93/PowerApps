package org.commercialtracking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class AddressBookStoreTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("address-book-test");
        AddressBookStore store = new AddressBookStore(root);
        store.save("J. Smith", "Cardiology", "x1234", "Deliver to front desk");
        store.save("j. smith", "", "", "Updated note");
        List<Map<String, String>> entries = store.load();
        check(entries.size() == 1, "case-insensitive upsert");
        check("Cardiology".equals(entries.get(0).get("department")), "blank assignment preserves department");
        check("Updated note".equals(entries.get(0).get("notes")), "notes update");
        System.out.println("AddressBookStoreTest passed");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
