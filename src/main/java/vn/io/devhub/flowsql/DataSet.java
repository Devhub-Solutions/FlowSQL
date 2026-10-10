package vn.io.devhub.flowsql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * DataSet — JDBC + HikariCP voi named parameter ({@code :name}) va transaction theo thread.
 *
 * <pre>{@code
 * DataSet db = DataSet.mysql().host("localhost").database("shop").user("app").pass("secret");
 *
 * // API strict: nem FlowSqlException khi loi
 * List<Map<String, Object>> rows = db.query("SELECT * FROM users WHERE id = :id", Map.of("id", 1));
 *
 * // API kieu devhub (giu tuong thich): setField -> searchAndRetrieve / update, loi -> getLastError()
 * db.setField("id", 1);
 * List<Map<String, Object>> same = db.searchAndRetrieve("SELECT * FROM users WHERE id = :id");
 *
 * db.inTransaction(() -> {
 *     db.execute("UPDATE acc SET bal = bal - :v WHERE id = :a", Map.of("v", 10, "a", 1));
 *     db.execute("UPDATE acc SET bal = bal + :v WHERE id = :b", Map.of("v", 10, "b", 2));
 * });
 * }</pre>
 *
 * <p>MySQL/MariaDB: moi connection duoc ep session time zone = UTC de {@code NOW()} va gio
 * phia Java khop nhau.</p>
 *
 * <p>Thread-safe: 1 instance dung chung nhieu thread; parameter, loi gan nhat va transaction
 * duoc giu rieng theo thread.</p>
 */
public class DataSet implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DataSet.class);

    static final String MYSQL_UTC_INIT_SQL = "SET time_zone = '+00:00'";

    private DBType dbType = DBType.MYSQL;
    private String hostname = "localhost";
    private int port = -1;
    private String databaseName;
    private String username;
    private String password;
    private String poolName;
    private boolean ssl;
    private String jdbcUrlOverride;
    private int maxPoolSize = 20;
    private int minIdle = 5;
    private long connectionTimeoutMs = 30_000;
    private long idleTimeoutMs = 600_000;
    private long maxLifetimeMs = 1_800_000;
    private long leakDetectionMs = 0;
    private long slowQueryMs = 0;

    private final ThreadLocal<Map<String, Object>> parameters = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<String> lastError = new ThreadLocal<>();
    private final ThreadLocal<Connection> transaction = new ThreadLocal<>();

    private volatile DataSource dataSource;
    private volatile boolean ownsDataSource;

    public DataSet() {}

    public DataSet(DBType type) { this.dbType = type; }

    /** Boc 1 DataSource co san (vd. cua Spring). DataSet KHONG dong pool nay khi close(). */
    public DataSet(DataSource external) {
        this.dataSource = external;
        this.ownsDataSource = false;
    }

    public static DataSet mysql() { return new DataSet(DBType.MYSQL); }
    public static DataSet mariadb() { return new DataSet(DBType.MARIADB); }
    public static DataSet postgres() { return new DataSet(DBType.POSTGRESQL); }
    /** H2 in-memory ({@code name}) hoac file ({@code "file:./data/app"}), che do tuong thich MySQL. */
    public static DataSet h2(String name) { return new DataSet(DBType.H2).database(name); }

    // ---- Cau hinh (builder) ----
    public DataSet dbType(DBType t) { this.dbType = t; return this; }
    public DataSet host(String h) { this.hostname = h; return this; }
    public DataSet port(int p) { this.port = p; return this; }
    public DataSet database(String d) { this.databaseName = d; return this; }
    public DataSet user(String u) { this.username = u; return this; }
    public DataSet pass(String p) { this.password = p; return this; }
    public DataSet poolName(String n) { this.poolName = n; return this; }
    public DataSet ssl(boolean s) { this.ssl = s; return this; }
    /** Dung JDBC URL tu viet thay vi URL sinh tu host/port/database. */
    public DataSet jdbcUrl(String url) { this.jdbcUrlOverride = url; return this; }
    public DataSet maxPoolSize(int n) { this.maxPoolSize = n; return this; }
    public DataSet minIdle(int n) { this.minIdle = n; return this; }
    public DataSet connectionTimeoutMs(long ms) { this.connectionTimeoutMs = ms; return this; }
    public DataSet idleTimeoutMs(long ms) { this.idleTimeoutMs = ms; return this; }
    public DataSet maxLifetimeMs(long ms) { this.maxLifetimeMs = ms; return this; }
    /** Canh bao connection bi giu lau hon nguong (0 = tat). */
    public DataSet leakDetectionMs(long ms) { this.leakDetectionMs = ms; return this; }
    /** Log WARN cau lenh chay lau hon nguong (0 = tat). */
    public DataSet slowQueryMs(long ms) { this.slowQueryMs = ms; return this; }

    public DBType dbType() { return dbType; }

    String buildJdbcUrl() {
        if (jdbcUrlOverride != null && !jdbcUrlOverride.isBlank()) return jdbcUrlOverride;
        int p = port > 0 ? port : dbType.defaultPort();
        return switch (dbType) {
            case MYSQL -> "jdbc:mysql://" + hostname + ":" + p + "/" + databaseName
                    + "?sslMode=" + (ssl ? "REQUIRED" : "DISABLED")
                    + "&connectionTimeZone=UTC"
                    + "&zeroDateTimeBehavior=CONVERT_TO_NULL&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true";
            case MARIADB -> "jdbc:mariadb://" + hostname + ":" + p + "/" + databaseName
                    + "?sslMode=" + (ssl ? "verify-full" : "disable");
            case POSTGRESQL -> "jdbc:postgresql://" + hostname + ":" + p + "/" + databaseName
                    + "?sslmode=" + (ssl ? "require" : "disable");
            case H2 -> (databaseName != null && databaseName.startsWith("file:")
                    ? "jdbc:h2:" + databaseName
                    : "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1")
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        };
    }

    static String connectionInitSql(DBType type) {
        return (type == DBType.MYSQL || type == DBType.MARIADB) ? MYSQL_UTC_INIT_SQL : null;
    }

    /** Pool duoc tao lazy o lan dung dau tien. */
    public DataSource getDataSource() {
        DataSource ds = dataSource;
        if (ds != null && !(ds instanceof HikariDataSource h && h.isClosed())) return ds;
        synchronized (this) {
            ds = dataSource;
            if (ds != null && !(ds instanceof HikariDataSource h && h.isClosed())) return ds;
            HikariConfig c = new HikariConfig();
            c.setJdbcUrl(buildJdbcUrl());
            c.setDriverClassName(dbType.driverClass());
            if (username != null) c.setUsername(username);
            if (password != null) c.setPassword(password);
            c.setMaximumPoolSize(maxPoolSize);
            c.setMinimumIdle(Math.min(minIdle, maxPoolSize));
            c.setConnectionTimeout(connectionTimeoutMs);
            c.setIdleTimeout(idleTimeoutMs);
            c.setMaxLifetime(maxLifetimeMs);
            if (leakDetectionMs > 0) c.setLeakDetectionThreshold(leakDetectionMs);
            c.setPoolName(poolName != null ? poolName : "flowsql-" + databaseName);
            String initSql = connectionInitSql(dbType);
            if (initSql != null) c.setConnectionInitSql(initSql);
            HikariDataSource created = new HikariDataSource(c);
            log.info("[{}] pool ready: type={}, maxPoolSize={}, minIdle={}",
                    c.getPoolName(), dbType, c.getMaximumPoolSize(), c.getMinimumIdle());
            dataSource = created;
            ownsDataSource = true;
            return created;
        }
    }

    // =====================================================================
    // API strict — nem FlowSqlException
    // =====================================================================

    public List<Map<String, Object>> query(String sql) { return query(sql, Map.of()); }

    public List<Map<String, Object>> query(String sql, Map<String, ?> params) {
        return run(sql, params, "QUERY", (c, parsed) -> {
            try (PreparedStatement ps = c.prepareStatement(parsed.sql())) {
                bind(ps, parsed.values());
                try (ResultSet rs = ps.executeQuery()) {
                    return ResultSetMapper.map(rs);
                }
            }
        });
    }

    /** Row dau tien hoac {@link Optional#empty()}. */
    public Optional<Map<String, Object>> queryOne(String sql, Map<String, ?> params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Gia tri cot dau tien cua row dau tien (vd. {@code SELECT COUNT(*) ...}). */
    public Object queryScalar(String sql, Map<String, ?> params) {
        return queryOne(sql, params).map(r -> r.isEmpty() ? null : r.values().iterator().next()).orElse(null);
    }

    /** INSERT/UPDATE/DELETE/DDL — tra ve so dong bi anh huong. */
    public int execute(String sql) { return execute(sql, Map.of()); }

    public int execute(String sql, Map<String, ?> params) {
        return run(sql, params, "EXECUTE", (c, parsed) -> {
            try (PreparedStatement ps = c.prepareStatement(parsed.sql())) {
                bind(ps, parsed.values());
                return ps.executeUpdate();
            }
        });
    }

    /** INSERT va tra ve khoa sinh tu dong (auto increment / identity), null neu khong co. */
    public Object insert(String sql, Map<String, ?> params) {
        return run(sql, params, "INSERT", (c, parsed) -> {
            try (PreparedStatement ps = c.prepareStatement(parsed.sql(), Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, parsed.values());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getObject(1) : null;
                }
            }
        });
    }

    /** Chay cung 1 cau lenh cho nhieu bo tham so (JDBC batch). Tra ve so dong theo tung bo. */
    public int[] batch(String sql, List<? extends Map<String, ?>> paramsList) {
        if (paramsList == null || paramsList.isEmpty()) return new int[0];
        return run(sql, paramsList.get(0), "BATCH", (c, first) -> {
            try (PreparedStatement ps = c.prepareStatement(first.sql())) {
                for (Map<String, ?> params : paramsList) {
                    bind(ps, NamedParamParser.parse(sql, params).values());
                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        });
    }

    // =====================================================================
    // API kieu devhub (tuong thich nguoc) — khong nem loi, xem getLastError()
    // =====================================================================

    public DataSet setField(String name, Object value) {
        parameters.get().put(name, value);
        return this;
    }

    public void clearFields() { parameters.get().clear(); }

    /** SELECT voi cac field da set; tra ve {@code null} neu loi (xem {@link #getLastError()}). */
    public List<Map<String, Object>> searchAndRetrieve(String sql) {
        lastError.remove();
        try {
            return query(sql, new HashMap<>(parameters.get()));
        } catch (RuntimeException e) {
            lastError.set(rootMessage(e));
            return null;
        } finally {
            clearFields();
        }
    }

    /** UPDATE/INSERT/DELETE voi cac field da set; tra ve 0 neu loi (xem {@link #getLastError()}). */
    public int update(String sql) {
        lastError.remove();
        try {
            return execute(sql, new HashMap<>(parameters.get()));
        } catch (RuntimeException e) {
            lastError.set(rootMessage(e));
            return 0;
        } finally {
            clearFields();
        }
    }

    public String getLastError() { return lastError.get(); }

    public void clearLastError() { lastError.remove(); }

    // =====================================================================
    // Transaction (theo thread)
    // =====================================================================

    public void begin() {
        if (transaction.get() != null) throw new IllegalStateException("Transaction da mo tren thread nay");
        try {
            Connection c = getDataSource().getConnection();
            c.setAutoCommit(false);
            transaction.set(c);
        } catch (SQLException e) {
            throw new FlowSqlException("Khong mo duoc transaction", e);
        }
    }

    public void commit() {
        Connection c = transaction.get();
        if (c == null) return;
        try {
            c.commit();
        } catch (SQLException e) {
            lastError.set(e.getMessage());
            throw new FlowSqlException("Commit that bai", e);
        } finally {
            release(c);
        }
    }

    public void rollback() {
        Connection c = transaction.get();
        if (c == null) return;
        try {
            c.rollback();
        } catch (SQLException e) {
            lastError.set(e.getMessage());
            log.warn("Rollback that bai: {}", e.getMessage());
        } finally {
            release(c);
        }
    }

    public boolean isInTransaction() { return transaction.get() != null; }

    /** Chay {@code work} trong 1 transaction: commit neu thanh cong, rollback neu co exception. */
    public void inTransaction(Runnable work) {
        inTransaction(() -> { work.run(); return null; });
    }

    public <T> T inTransaction(Supplier<T> work) {
        if (isInTransaction()) return work.get(); // tham gia transaction dang mo
        begin();
        try {
            T result = work.get();
            commit();
            return result;
        } catch (RuntimeException | Error e) {
            rollback();
            throw e;
        }
    }

    private void release(Connection c) {
        try { c.setAutoCommit(true); } catch (SQLException ignored) { /* connection hong, pool se loai */ }
        closeQuietly(c);
        transaction.remove();
    }

    // =====================================================================
    // Noi bo
    // =====================================================================

    @FunctionalInterface
    private interface Work<T> {
        T apply(Connection c, NamedParamParser.ParsedSql parsed) throws SQLException;
    }

    private <T> T run(String sql, Map<String, ?> params, String op, Work<T> work) {
        long start = System.nanoTime();
        Connection tx = transaction.get();
        Connection c = tx;
        try {
            NamedParamParser.ParsedSql parsed = NamedParamParser.parse(sql, params);
            if (c == null) c = getDataSource().getConnection();
            T result = work.apply(c, parsed);
            logSlow(op, sql, start);
            return result;
        } catch (SQLException | IllegalArgumentException e) {
            log.error("[{}] FAILED: {} | sql={}", op, e.getMessage(), sql);
            throw new FlowSqlException("[" + op + "] " + e.getMessage(), e);
        } finally {
            if (tx == null) closeQuietly(c);
        }
    }

    private void logSlow(String op, String sql, long startNanos) {
        if (slowQueryMs <= 0) return;
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        if (ms >= slowQueryMs) log.warn("[{}] SLOW {} ms | sql={}", op, ms, sql);
    }

    private static void bind(PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) ps.setObject(i + 1, values.get(i));
    }

    private static void closeQuietly(AutoCloseable c) {
        try { if (c != null) c.close(); } catch (Exception ignored) { }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : e.getMessage();
    }

    /** Rollback transaction cua thread hien tai (neu co) va dong pool do DataSet tao ra. */
    @Override
    public synchronized void close() {
        if (transaction.get() != null) rollback();
        if (ownsDataSource && dataSource instanceof HikariDataSource h && !h.isClosed()) h.close();
    }

    /** Tien ich: tao Map tham so tu cap key/value {@code params("id", 1, "name", "x")}. */
    public static Map<String, Object> params(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("Can so phan tu chan (key, value, ...)");
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        return m;
    }

}
