package org.medsupply;

import java.util.List;
import java.util.Map;

public final class JsonTest {
    public static void main(String[] args) {
        roundTripScalarsAndNesting();
        parsesGudidShapedResponse();
        writesWholeNumbersWithoutDecimal();
        escapesStrings();
        rejectsExcessiveNesting();
        System.out.println("JsonTest: PASS");
    }

    private static void roundTripScalarsAndNesting() {
        String json = "{\"a\":\"x\",\"n\":3,\"b\":true,\"z\":null,\"arr\":[1,2,{\"k\":\"v\"}]}";
        Object parsed = Json.parse(json);
        Map<String, Object> m = Json.asMap(parsed);
        check("x".equals(Json.str(m, "a")), "a");
        check("3".equals(Json.str(m, "n")), "n");
        check(Boolean.TRUE.equals(m.get("b")), "b");
        check(m.containsKey("z") && m.get("z") == null, "z null");
        List<Object> arr = Json.asList(m.get("arr"));
        check(arr.size() == 3, "arr size");
        check("v".equals(Json.str(Json.asMap(arr.get(2)), "k")), "nested k");
    }

    private static void parsesGudidShapedResponse() {
        String json = "{\"device\":{\"brandName\":\"XIENCE\",\"companyName\":\"ABBOTT\","
                + "\"gmdnTerms\":{\"gmdn\":[{\"gmdnPTName\":\"Coronary stent\"}]}}}";
        Map<String, Object> root = Json.asMap(Json.parse(json));
        Map<String, Object> device = Json.asMap(root.get("device"));
        check("XIENCE".equals(Json.str(device, "brandName")), "brandName");
        check("ABBOTT".equals(Json.str(device, "companyName")), "companyName");
    }

    private static void writesWholeNumbersWithoutDecimal() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("q", Double.valueOf(5));
        check("{\"q\":5}".equals(Json.write(m)), "whole number: " + Json.write(m));
    }

    private static void escapesStrings() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("s", "a\"b\\c\n");
        String written = Json.write(m);
        check(written.equals("{\"s\":\"a\\\"b\\\\c\\n\"}"), "escape: " + written);
        check("a\"b\\c\n".equals(Json.str(Json.asMap(Json.parse(written)), "s")), "escape roundtrip");
    }

    private static void rejectsExcessiveNesting() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 102; i++) value.append('[');
        for (int i = 0; i < 102; i++) value.append(']');
        boolean rejected = false;
        try { Json.parse(value.toString()); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "depth cap");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
