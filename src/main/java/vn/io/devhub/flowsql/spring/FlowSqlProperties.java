package vn.io.devhub.flowsql.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cau hinh trong application.yml:
 * <pre>
 * flowsql:
 *   datasources:
 *     main:
 *       type: mysql        # mysql | mariadb | postgresql | h2
 *       host: db.example.com
 *       port: 3306
 *       database: app
 *       username: app
 *       password: ${DB_PASS}
 *     report:
 *       type: postgresql
 *       jdbc-url: jdbc:postgresql://report:5432/bi
 * </pre>
 */
@ConfigurationProperties(prefix = "flowsql")
public class FlowSqlProperties {

    private Map<String, Source> datasources = new LinkedHashMap<>();

    public Map<String, Source> getDatasources() { return datasources; }
    public void setDatasources(Map<String, Source> datasources) { this.datasources = datasources; }

    public static class Source {
        private String type = "mysql";
        private String host = "localhost";
        private int port = 0;
        private String database;
        private String username;
        private String password;
        private String jdbcUrl;
        private boolean ssl;
        private int maxPoolSize = 20;
        private int minIdle = 5;
        private long leakDetectionMs = 0;
        private long slowQueryMs = 0;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        public long getLeakDetectionMs() { return leakDetectionMs; }
        public void setLeakDetectionMs(long leakDetectionMs) { this.leakDetectionMs = leakDetectionMs; }
        public long getSlowQueryMs() { return slowQueryMs; }
        public void setSlowQueryMs(long slowQueryMs) { this.slowQueryMs = slowQueryMs; }
    }
}
