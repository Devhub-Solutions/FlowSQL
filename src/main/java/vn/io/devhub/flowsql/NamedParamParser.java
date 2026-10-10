package vn.io.devhub.flowsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Chuyen SQL co named parameter ({@code :name}) thanh SQL JDBC dung {@code ?} kem danh sach gia tri.
 *
 * <p>Bo qua nhung cho KHONG phai parameter:</p>
 * <ul>
 *   <li>chuoi trong nhay don / nhay kep / backtick: {@code '10:30'}, {@code "a:b"}</li>
 *   <li>comment {@code -- ...} va {@code /* ... *&#47;}</li>
 *   <li>cast kieu PostgreSQL {@code col::text}</li>
 * </ul>
 * Mot ten dung nhieu lan ({@code :id ... :id}) se duoc bind nhieu lan.
 */
public final class NamedParamParser {

    private NamedParamParser() {}

    /** Ket qua parse: SQL voi {@code ?} va gia tri theo dung thu tu placeholder. */
    public record ParsedSql(String sql, List<Object> values) {}

    public static ParsedSql parse(String sql, Map<String, ?> params) {
        if (sql == null || sql.isBlank()) return new ParsedSql(sql, List.of());
        Map<String, ?> p = params == null ? Collections.emptyMap() : params;
        StringBuilder out = new StringBuilder(sql.length());
        List<Object> values = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            // chuoi / identifier co quote
            if (c == '\'' || c == '"' || c == '`') {
                int end = skipQuoted(sql, i, c);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            // comment dong
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            // comment khoi
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == ':') {
                // PostgreSQL cast "::type"
                if (i + 1 < n && sql.charAt(i + 1) == ':') {
                    out.append("::");
                    i += 2;
                    continue;
                }
                if (i + 1 < n && isIdentStart(sql.charAt(i + 1))) {
                    int j = i + 2;
                    while (j < n && isIdentPart(sql.charAt(j))) j++;
                    String name = sql.substring(i + 1, j);
                    if (!p.containsKey(name)) {
                        throw new IllegalArgumentException("Thieu gia tri cho parameter :" + name);
                    }
                    values.add(p.get(name));
                    out.append('?');
                    i = j;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return new ParsedSql(out.toString(), Collections.unmodifiableList(values));
    }

    /** Tra ve vi tri ngay sau dau quote dong; ho tro escape bang cach nhan doi quote ('it''s'). */
    private static int skipQuoted(String sql, int start, char q) {
        int n = sql.length();
        int i = start + 1;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\\' && q != '`' && i + 1 < n) { // MySQL backslash escape
                i += 2;
                continue;
            }
            if (c == q) {
                if (i + 1 < n && sql.charAt(i + 1) == q) { i += 2; continue; }
                return i + 1;
            }
            i++;
        }
        return n;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
