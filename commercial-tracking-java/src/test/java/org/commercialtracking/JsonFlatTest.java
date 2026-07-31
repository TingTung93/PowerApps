package org.commercialtracking;

import java.util.Map;

public final class JsonFlatTest {
    public static void main(String[] args) {
        Map<String, String> values = JsonFlat.read(
                "{\"raw\":\"1Z999AA10123456784\",\"confirmed\":false,\"observedRevision\":-1,\"optional\":null}");
        check("1Z999AA10123456784".equals(values.get("raw")), "string");
        check("false".equals(values.get("confirmed")), "boolean");
        check("-1".equals(values.get("observedRevision")), "integer");
        check("".equals(values.get("optional")), "null");
        try {
            JsonFlat.read("{\"value\":unquoted}");
            throw new AssertionError("invalid primitive accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("JsonFlatTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
