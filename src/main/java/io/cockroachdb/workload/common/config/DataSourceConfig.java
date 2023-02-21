package io.cockroachdb.workload.common.config;

import java.sql.Connection;

import javax.sql.DataSource;

import org.postgresql.PGProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import com.zaxxer.hikari.HikariDataSource;

import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

@Configuration
public class DataSourceConfig {
    public static final String SQL_TRACE_LOGGER = "io.cockroachdb.SQL_TRACE";

    @Value("${spring.application.name}")
    private String appName;

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        LazyConnectionDataSourceProxy proxy = new LazyConnectionDataSourceProxy();
        proxy.setTargetDataSource(loggingProxy(targetDataSource()));
        proxy.setDefaultAutoCommit(true);
        proxy.setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        return proxy;
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource targetDataSource() {
        HikariDataSource ds = dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), true);
        ds.addDataSourceProperty(PGProperty.APPLICATION_NAME.getName(), appName);
        return ds;
    }

    private DataSource loggingProxy(DataSource dataSource) {
        return ProxyDataSourceBuilder
                .create(dataSource)
                .name("SQL-Trace")
                .asJson()
                .countQuery()
                .logQueryBySlf4j(SLF4JLogLevel.TRACE, SQL_TRACE_LOGGER)
                .multiline()
                .build();
    }
}
