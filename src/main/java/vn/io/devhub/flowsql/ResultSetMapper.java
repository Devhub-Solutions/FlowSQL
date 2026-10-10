package vn.io.devhub.flowsql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Map {@link ResultSet} thanh {@code List<Map>} voi key la ten cot viet thuong (LOWERCASE).
 * Dung {@link Rows} de doc gia tri khong phan biet hoa thuong.
 */
final class ResultSetMapper {
    private ResultSetMapper() {}

    static List<Map<String, Object>> map(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        String[] labels = new String[count];
        for (int i = 1; i <= count; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isEmpty()) label = meta.getColumnName(i);
            labels[i - 1] = label.toLowerCase(Locale.ROOT);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(count * 2);
            for (int i = 1; i <= count; i++) row.put(labels[i - 1], rs.getObject(i));
            rows.add(row);
        }
        return rows;
    }
}
