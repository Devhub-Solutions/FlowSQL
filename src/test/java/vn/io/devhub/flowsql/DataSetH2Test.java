package vn.io.devhub.flowsql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static vn.io.devhub.flowsql.DataSet.params;

class DataSetH2Test {

    static DataSet db;

    @BeforeAll
    static void setUp() {
        db = DataSet.h2("flowsql_test").maxPoolSize(4).minIdle(1);
        db.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL, active BOOLEAN)");
    }

    @AfterAll
    static void tearDown() { db.close(); }

    @Test
    void insertQueryAndRows() {
        Object id = db.insert("INSERT INTO users(name, active) VALUES(:n, :a)", params("n", "Thanh", "a", true));
        assertNotNull(id);
        Map<String, Object> row = db.queryOne("SELECT * FROM users WHERE id = :id", params("id", id)).orElseThrow();
        assertEquals("Thanh", Rows.str(row, "NAME"));
        assertTrue(Rows.getBool(row, "Active", false));
    }

    @Test
    void legacyApiReportsErrorsWithoutThrowing() {
        assertNull(db.searchAndRetrieve("SELECT * FROM no_such_table"));
        assertNotNull(db.getLastError());
        db.setField("n", "Legacy");
        assertEquals(1, db.update("INSERT INTO users(name) VALUES(:n)"));
        assertNull(db.getLastError());
        List<Map<String, Object>> rows = DbUtil.safeQuery(db, "SELECT * FROM users WHERE name = :n", "n", "Legacy");
        assertEquals(1, rows.size());
    }

    @Test
    void transactionRollsBackOnException() {
        long before = Rows.getLong(db.queryOne("SELECT COUNT(*) c FROM users", Map.of()).orElseThrow(), "c", -1);
        assertThrows(IllegalStateException.class, () -> db.inTransaction(() -> {
            db.execute("INSERT INTO users(name) VALUES(:n)", params("n", "tx"));
            throw new IllegalStateException("boom");
        }));
        long after = ((Number) db.queryScalar("SELECT COUNT(*) FROM users", Map.of())).longValue();
        assertEquals(before, after);
        assertFalse(db.isInTransaction());
    }

    @Test
    void transactionIsPerThread() throws Exception {
        db.begin();
        try {
            db.execute("INSERT INTO users(name) VALUES(:n)", params("n", "uncommitted"));
            AtomicReference<Boolean> otherThreadInTx = new AtomicReference<>();
            Thread t = new Thread(() -> otherThreadInTx.set(db.isInTransaction()));
            t.start();
            t.join();
            assertFalse(otherThreadInTx.get());
        } finally {
            db.rollback();
        }
        assertTrue(db.query("SELECT * FROM users WHERE name = :n", params("n", "uncommitted")).isEmpty());
    }

    @Test
    void batchInsert() {
        int[] counts = db.batch("INSERT INTO users(name) VALUES(:n)",
                List.of(params("n", "b1"), params("n", "b2"), params("n", "b3")));
        assertEquals(3, counts.length);
        assertEquals(3, db.query("SELECT * FROM users WHERE name LIKE 'b%'").size());
    }

    @Test
    void strictApiThrows() {
        assertThrows(FlowSqlException.class, () -> db.query("SELECT * FROM missing"));
    }

    @Test
    void registry() {
        try (DataSetRegistry r = new DataSetRegistry()) {
            r.register("main", DataSet.h2("flowsql_reg"));
            assertEquals(1, ((Number) r.get("main").queryScalar("SELECT 1", Map.of())).intValue());
            assertThrows(IllegalStateException.class, () -> r.get("other"));
        }
    }

    @Test
    void jdbcUrls() {
        assertTrue(DataSet.mysql().host("h").database("d").buildJdbcUrl().startsWith("jdbc:mysql://h:3306/d?"));
        assertEquals("jdbc:postgresql://h:5433/d?sslmode=require",
                DataSet.postgres().host("h").port(5433).database("d").ssl(true).buildJdbcUrl());
        assertEquals(DBType.POSTGRESQL, DBType.of("pg"));
    }
}
