package vn.io.devhub.flowsql;

import java.util.List;
import java.util.Map;

/**
 * Helper kieu devhub-solutions: {@code safeQuery(ds, sql, "k1", v1, "k2", v2)}.
 * Giu de code cu chuyen sang thu vien nay khong phai sua; code moi nen dung
 * {@link DataSet#query(String, Map)} va {@link Rows}.
 */
public final class DbUtil {
    private DbUtil() {}

    public static List<Map<String, Object>> safeQuery(DataSet ds, String sql, Object... fieldPairs) {
        setFields(ds, fieldPairs);
        return ds.searchAndRetrieve(sql);
    }

    public static int safeUpdate(DataSet ds, String sql, Object... fieldPairs) {
        setFields(ds, fieldPairs);
        return ds.update(sql);
    }

    private static void setFields(DataSet ds, Object... fieldPairs) {
        if (fieldPairs.length % 2 != 0) throw new IllegalArgumentException("fieldPairs phai co so phan tu chan (ten, gia tri, ...)");
        ds.clearFields();
        for (int i = 0; i < fieldPairs.length; i += 2) ds.setField(String.valueOf(fieldPairs[i]), fieldPairs[i + 1]);
    }

    public static Object getVal(Map<String, Object> row, String key) { return Rows.get(row, key); }
    public static String getStr(Map<String, Object> row, String key) { return Rows.str(row, key); }
    public static long getLong(Map<String, Object> row, String key, long def) { return Rows.getLong(row, key, def); }
    public static int getInt(Map<String, Object> row, String key, int def) { return Rows.getInt(row, key, def); }
    public static boolean getBool(Map<String, Object> row, String key, boolean def) { return Rows.getBool(row, key, def); }
}
