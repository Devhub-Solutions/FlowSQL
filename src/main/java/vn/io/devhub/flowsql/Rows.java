package vn.io.devhub.flowsql;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Doc gia tri tu 1 row ({@code Map<String,Object>}) khong phan biet hoa thuong:
 * thu exact -> lowercase -> uppercase. Ep kieu an toan voi gia tri mac dinh.
 */
public final class Rows {
    private Rows() {}

    public static Object get(Map<String, Object> row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v != null) return v;
        v = row.get(key.toLowerCase(Locale.ROOT));
        if (v != null) return v;
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    public static String str(Map<String, Object> row, String key) {
        Object v = get(row, key);
        return v != null ? String.valueOf(v) : null;
    }

    public static String str(Map<String, Object> row, String key, String def) {
        String v = str(row, key);
        return v != null ? v : def;
    }

    public static long getLong(Map<String, Object> row, String key, long def) {
        Object v = get(row, key);
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v).trim()); } catch (NumberFormatException e) { return def; }
    }

    public static int getInt(Map<String, Object> row, String key, int def) {
        return (int) getLong(row, key, def);
    }

    public static BigDecimal getDecimal(Map<String, Object> row, String key, BigDecimal def) {
        Object v = get(row, key);
        if (v == null) return def;
        if (v instanceof BigDecimal b) return b;
        try { return new BigDecimal(String.valueOf(v).trim()); } catch (NumberFormatException e) { return def; }
    }

    public static boolean getBool(Map<String, Object> row, String key, boolean def) {
        Object v = get(row, key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim();
        return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("Y");
    }
}
