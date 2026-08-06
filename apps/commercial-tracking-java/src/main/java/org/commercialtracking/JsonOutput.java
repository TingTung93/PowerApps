package org.commercialtracking;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JsonOutput {
    private JsonOutput() {}

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            quote(out, (String)value);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map) {
            out.append('{');
            Iterator<Map.Entry<Object, Object>> iterator =
                    ((Map<Object, Object>)value).entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Object> entry = iterator.next();
                quote(out, String.valueOf(entry.getKey()));
                out.append(':');
                append(out, entry.getValue());
                if (iterator.hasNext()) out.append(',');
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            Iterator<Object> iterator = ((List<Object>)value).iterator();
            while (iterator.hasNext()) {
                append(out, iterator.next());
                if (iterator.hasNext()) out.append(',');
            }
            out.append(']');
        } else {
            quote(out, String.valueOf(value));
        }
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        out.append('"');
    }
}
