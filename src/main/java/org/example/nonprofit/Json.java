package org.example.nonprofit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (parser.index != text.length()) throw new IllegalArgumentException("Trailing JSON data");
        return value;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return '"' + escape(s) + '"';
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (out.length() > 1) out.append(',');
                out.append(stringify(entry.getKey().toString())).append(':').append(stringify(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            for (Object item : values) {
                if (out.length() > 1) out.append(',');
                out.append(stringify(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String text;
        private int index;
        private Parser(String text) { this.text = text; }

        private Object value() {
            space();
            if (index >= text.length()) throw new IllegalArgumentException("Missing JSON value");
            return switch (text.charAt(index)) {
                case '{' -> object(); case '[' -> array(); case '"' -> string();
                case 't' -> literal("true", true); case 'f' -> literal("false", false);
                case 'n' -> literal("null", null); default -> number();
            };
        }
        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>(); index++; space();
            if (take('}')) return out;
            do { space(); String key = string(); space(); expect(':'); out.put(key, value()); space(); }
            while (take(','));
            expect('}'); return out;
        }
        private List<Object> array() {
            List<Object> out = new ArrayList<>(); index++; space();
            if (take(']')) return out;
            do { out.add(value()); space(); } while (take(','));
            expect(']'); return out;
        }
        private String string() {
            expect('"'); StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                char e = text.charAt(index++);
                if (e == 'u') { out.append((char) Integer.parseInt(text.substring(index, index + 4), 16)); index += 4; }
                else out.append(switch (e) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'b' -> '\b'; case 'f' -> '\f'; default -> e; });
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }
        private Object number() {
            int start = index;
            while (index < text.length() && "-+0123456789.eE".indexOf(text.charAt(index)) >= 0) index++;
            String token = text.substring(start, index);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }
        private Object literal(String token, Object value) {
            if (!text.startsWith(token, index)) throw new IllegalArgumentException("Invalid JSON literal");
            index += token.length(); return value;
        }
        private void space() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
        private boolean take(char c) { if (index < text.length() && text.charAt(index) == c) { index++; return true; } return false; }
        private void expect(char c) { if (!take(c)) throw new IllegalArgumentException("Expected " + c); }
    }
}

