package vn.io.devhub.flowsql.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import vn.io.devhub.flowsql.DBType;
import vn.io.devhub.flowsql.DataSet;
import vn.io.devhub.flowsql.DataSetRegistry;

import java.util.Map;

/**
 * Spring Boot auto-config: moi muc trong {@code flowsql.datasources.*} thanh 1 DataSet
 * trong bean {@link DataSetRegistry}. Pool duoc tao lazy o lan truy van dau tien.
 */
@AutoConfiguration
@ConditionalOnClass(DataSet.class)
@EnableConfigurationProperties(FlowSqlProperties.class)
public class FlowSqlAutoConfiguration {

    @Bean(destroyMethod = "closeAll")
    @ConditionalOnMissingBean
    public DataSetRegistry dataSetRegistry(FlowSqlProperties props) {
        DataSetRegistry registry = new DataSetRegistry();
        for (Map.Entry<String, FlowSqlProperties.Source> e : props.getDatasources().entrySet()) {
            registry.register(e.getKey(), toDataSet(e.getKey(), e.getValue()));
        }
        return registry;
    }

    static DataSet toDataSet(String name, FlowSqlProperties.Source s) {
        DataSet ds = new DataSet(DBType.of(s.getType()))
                .host(s.getHost())
                .port(s.getPort())
                .database(s.getDatabase())
                .user(s.getUsername())
                .pass(s.getPassword())
                .ssl(s.isSsl())
                .maxPoolSize(s.getMaxPoolSize())
                .minIdle(s.getMinIdle())
                .leakDetectionMs(s.getLeakDetectionMs())
                .slowQueryMs(s.getSlowQueryMs())
                .poolName("flowsql-" + name);
        if (s.getJdbcUrl() != null) ds.jdbcUrl(s.getJdbcUrl());
        return ds;
    }
}
