package org.medsupply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private static final int MAX_DEPTH = 100;
    private Json() {}

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("null JSON");
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.readValue(0);
        p.skipWs();
        if (!p.atEnd()) throw new IllegalArgumentException("Trailing JSON content at " + p.pos);
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value
                : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return "";
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.rint(d) && !Double.isInfinite(d)) return Long.toString((long) d);
            return Double.toString(d);
        }
        return String.valueOf(value);
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(e.getKey()));
                out.append(':');
                writeValue(out, e.getValue());
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) out.append(',');
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) out.append(Long.toString((long) d));
            else out.append(Double.toString(d));
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) throw new IllegalArgumentException("JSON nesting exceeds " + MAX_DEPTH);
            skipWs();
            if (atEnd()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return readObject(depth + 1);
                case '[': return readArray(depth + 1);
                case '"': return readString();
                case 't': case 'f': return readBoolean();
                case 'n': expect("null"); return null;
                default: return readNumber();
            }
        }

        private Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            pos++; // {
            skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                if (peek() != '"') throw new IllegalArgumentException("Expected key at " + pos);
                String key = readString();
                skipWs();
                if (peek() != ':') throw new IllegalArgumentException("Expected ':' at " + pos);
                pos++;
                map.put(key, readValue(depth));
                skipWs();
                char n = peek();
                if (n == ',') { pos++; continue; }
                if (n == '}') { pos++; break; }
                throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
            }
            return map;
        }

        private List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<Object>();
            pos++; // [
            skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(readValue(depth));
                skipWs();
                char n = peek();
                if (n == ',') { pos++; continue; }
                if (n == ']') { pos++; break; }
                throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
            }
            return list;
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) break;
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw new IllegalArgumentException("Bad \\u escape");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw new IllegalArgumentException("Bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Boolean readBoolean() {
            if (s.charAt(pos) == 't') { expect("true"); return Boolean.TRUE; }
            expect("false");
            return Boolean.FALSE;
        }

        private Double readNumber() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            if (pos == start) throw new IllegalArgumentException("Invalid number at " + start);
            return Double.valueOf(s.substring(start, pos));
        }

        private void expect(String literal) {
            if (!s.regionMatches(pos, literal, 0, literal.length()))
                throw new IllegalArgumentException("Expected '" + literal + "' at " + pos);
            pos += literal.length();
        }

        private char peek() {
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(pos);
        }
    }
}
