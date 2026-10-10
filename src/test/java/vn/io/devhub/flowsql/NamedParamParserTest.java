package vn.io.devhub.flowsql;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NamedParamParserTest {

    @Test
    void replacesNamedParamsInOrder() {
        var p = NamedParamParser.parse("SELECT * FROM t WHERE a = :a AND b = :b_2 OR a = :a",
                Map.of("a", 1, "b_2", "x"));
        assertEquals("SELECT * FROM t WHERE a = ? AND b = ? OR a = ?", p.sql());
        assertEquals(List.of(1, "x", 1), p.values());
    }

    @Test
    void ignoresPostgresCastLiteralsAndComments() {
        String sql = "SELECT id::text, '10:30' AS t, \"a:b\" -- :nope\n FROM x /* :nope2 */ WHERE id = :id";
        var p = NamedParamParser.parse(sql, Map.of("id", 7));
        assertEquals("SELECT id::text, '10:30' AS t, \"a:b\" -- :nope\n FROM x /* :nope2 */ WHERE id = ?", p.sql());
        assertEquals(List.of(7), p.values());
    }

    @Test
    void handlesEscapedQuotes() {
        var p = NamedParamParser.parse("SELECT 'it''s :x' WHERE a = :a", Map.of("a", 1));
        assertEquals("SELECT 'it''s :x' WHERE a = ?", p.sql());
    }

    @Test
    void nullValueAllowedWhenKeyPresent() {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("a", null);
        var p = NamedParamParser.parse("UPDATE t SET a = :a", m);
        assertEquals(1, p.values().size());
        assertNull(p.values().get(0));
    }

    @Test
    void missingParamFails() {
        assertThrows(IllegalArgumentException.class, () -> NamedParamParser.parse("SELECT :a", Map.of()));
    }
}
