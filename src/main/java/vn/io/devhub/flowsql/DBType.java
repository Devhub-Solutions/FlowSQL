package vn.io.devhub.flowsql;

import java.util.Locale;

/** Cac he CSDL duoc ho tro san (URL, driver, init SQL). */
public enum DBType {
    MYSQL("com.mysql.cj.jdbc.Driver", 3306),
    MARIADB("org.mariadb.jdbc.Driver", 3306),
    POSTGRESQL("org.postgresql.Driver", 5432),
    H2("org.h2.Driver", -1);

    private final String driverClass;
    private final int defaultPort;

    DBType(String driverClass, int defaultPort) {
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
    }

    public String driverClass() { return driverClass; }

    public int defaultPort() { return defaultPort; }

    /** Parse khong phan biet hoa thuong; chap nhan "postgres"/"pg" cho POSTGRESQL. */
    public static DBType of(String value) {
        if (value == null || value.isBlank()) return MYSQL;
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("POSTGRES") || v.equals("PG")) return POSTGRESQL;
        return DBType.valueOf(v);
    }
}
