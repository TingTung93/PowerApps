package org.commercialtracking;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonFlat {
    private JsonFlat() {}

    public static String write(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{\n");
        int index = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            out.append("  \"").append(escape(entry.getKey())).append("\": \"")
                    .append(escape(entry.getValue())).append("\"");
            if (++index < fields.size()) out.append(',');
            out.append('\n');
        }
        return out.append("}\n").toString();
    }

    public static Map<String, String> read(String json) {
        if (json == null || json.length() > 1024 * 1024) throw new IllegalArgumentException("Invalid JSON size");
        Parser parser = new Parser(json);
        return parser.parse();
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
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
        return out.toString();
    }

    private static final class Parser {
        private final String text;
        private int p;
        Parser(String text) { this.text = text; }

        Map<String, String> parse() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            space(); expect('{'); space();
            if (peek('}')) { p++; return values; }
            while (true) {
                String key = string();
                space(); expect(':'); space();
                String value = string();
                values.put(key, value);
                space();
                if (peek('}')) { p++; break; }
                expect(','); space();
            }
            space();
            if (p != text.length()) throw new IllegalArgumentException("Trailing JSON content");
            return values;
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (p < text.length()) {
                char c = text.charAt(p++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (p >= text.length()) throw new IllegalArgumentException("Invalid escape");
                char e = text.charAt(p++);
                switch (e) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (p + 4 > text.length()) throw new IllegalArgumentException("Invalid unicode escape");
                        out.append((char)Integer.parseInt(text.substring(p, p + 4), 16));
                        p += 4; break;
                    default: throw new IllegalArgumentException("Invalid escape");
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private void space() { while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++; }
        private boolean peek(char c) { return p < text.length() && text.charAt(p) == c; }
        private void expect(char c) {
            if (!peek(c)) throw new IllegalArgumentException("Expected " + c + " at " + p);
            p++;
        }
    }
}
