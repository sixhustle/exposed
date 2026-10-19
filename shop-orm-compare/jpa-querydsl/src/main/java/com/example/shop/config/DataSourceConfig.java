package com.example.shop.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Writer(MySQL 소스) / Reader(MySQL 레플리카) 라우팅.
 *
 * LazyConnectionDataSourceProxy#setReadOnlyDataSource (Spring 6.1+)
 * - 트랜잭션이 시작돼도 물리 커넥션을 바로 얻지 않고 첫 SQL 시점에 얻는다.
 * - 그 사이 Connection.setReadOnly(true)가 호출됐으면 reader 풀, 아니면 writer 풀.
 * - setReadOnly(true)를 부르는 주체는 JpaTransactionManager(HibernateJpaDialect)다.
 *   그래서 개발자가 설정하는 곳은 @Transactional(readOnly = true) 하나다 (보고서 코드 G).
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    @Bean(defaultCandidate = false)
    @FlywayDataSource // 마이그레이션은 소스로 (reader 계정은 SELECT만 가능)
    @ConfigurationProperties("app.datasource.writer")
    public HikariDataSource writerDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(defaultCandidate = false)
    @ConfigurationProperties("app.datasource.reader")
    public HikariDataSource readerDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("writerDataSource") DataSource writer,
                                 @Qualifier("readerDataSource") DataSource reader) {
        LazyConnectionDataSourceProxy proxy = new LazyConnectionDataSourceProxy(writer);
        proxy.setReadOnlyDataSource(reader);
        proxy.setDefaultAutoCommit(true);
        proxy.setDefaultTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        return proxy;
    }
}
