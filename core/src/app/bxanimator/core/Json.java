package app.bxanimator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency free JSON reader/writer.
 *
 * <p>Objects are {@link LinkedHashMap}, arrays are {@link ArrayList}, numbers are
 * {@link Double}, booleans are {@link Boolean}, strings are {@link String}. The
 * order of keys is preserved, which keeps project files diff friendly.</p>
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("json text is null");
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("trailing data at offset " + p.pos);
        }
        return value;
    }

    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new IllegalArgumentException("root is not an object");
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private Object readValue() {
        if (pos >= src.length()) throw new IllegalArgumentException("unexpected end of json");
        char c = src.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                throw new IllegalArgumentException("unexpected character '" + c + "' at " + pos);
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            if (peek() != ':') throw new IllegalArgumentException("expected ':' at " + pos);
            pos++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return map;
            }
            throw new IllegalArgumentException("expected ',' or '}' at " + pos);
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return list;
            }
            throw new IllegalArgumentException("expected ',' or ']' at " + pos);
        }
    }

    private String readString() {
        if (peek() != '"') throw new IllegalArgumentException("expected string at " + pos);
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) throw new IllegalArgumentException("unterminated string");
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= src.length()) throw new IllegalArgumentException("unterminated escape");
            char e = src.charAt(pos++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > src.length()) throw new IllegalArgumentException("bad unicode escape");
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    throw new IllegalArgumentException("bad escape \\" + e);
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) throw new IllegalArgumentException("expected " + word + " at " + pos);
        pos += word.length();
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
            else break;
        }
    }

    // ------------------------------------------------------------------ write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append('0');
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(round(d));
            }
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else if (value instanceof float[] || value instanceof int[]) {
            sb.append('[');
            if (value instanceof float[]) {
                float[] arr = (float[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(round(arr[i]));
                }
            } else {
                int[] arr = (int[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(arr[i]);
                }
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static String round(double d) {
        String s = String.format(java.util.Locale.US, "%.5f", d);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- helpers

    public static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    public static List<Object> arr(Object... values) {
        List<Object> list = new ArrayList<>(values.length);
        for (Object v : values) list.add(v);
        return list;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (value instanceof Map) return (Map<String, Object>) value;
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) {
        if (value instanceof List) return (List<Object>) value;
        return new ArrayList<>();
    }

    public static String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : def;
    }

    public static double num(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static float fnum(Map<String, Object> map, String key, float def) {
        return (float) num(map, key, def);
    }

    public static int inum(Map<String, Object> map, String key, int def) {
        return (int) Math.round(num(map, key, def));
    }

    public static long lnum(Map<String, Object> map, String key, long def) {
        return (long) Math.round(num(map, key, def));
    }

    public static double number(Object value, double def) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    /** Writes a colour as #AARRGGBB. */
    public static String hex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    /** Reads #RGB, #RRGGBB, #AARRGGBB or a decimal integer. */
    public static int color(Object value, int def) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (!(value instanceof String)) return def;
        String s = ((String) value).trim();
        if (s.isEmpty()) return def;
        try {
            if (s.startsWith("#")) {
                s = s.substring(1);
                if (s.length() == 3) {
                    int r = Integer.parseInt(s.substring(0, 1), 16);
                    int g = Integer.parseInt(s.substring(1, 2), 16);
                    int b = Integer.parseInt(s.substring(2, 3), 16);
                    return 0xFF000000 | (r * 17 << 16) | (g * 17 << 8) | (b * 17);
                }
                if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
                if (s.length() == 8) return (int) Long.parseLong(s, 16);
            }
            return (int) Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
