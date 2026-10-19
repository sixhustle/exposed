package com.example.shop.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import java.sql.Connection
import javax.sql.DataSource

/**
 * Writer(MySQL 소스) / Reader(MySQL 레플리카) 라우팅. (세 프로젝트가 같은 구성을 각자 갖는다)
 *
 * LazyConnectionDataSourceProxy#setReadOnlyDataSource (Spring 6.1+)
 * - 트랜잭션이 시작돼도 물리 커넥션을 바로 얻지 않고 첫 SQL 시점에 얻는다.
 * - 그 사이 Connection.setReadOnly(true)가 호출됐으면 reader 풀, 아니면 writer 풀.
 * - setReadOnly(true)를 부르는 주체는 트랜잭션 매니저다.
 *   Exposed에서는 starter의 SpringTransactionManager.
 *   그래서 개발자가 설정하는 곳은 @Transactional(readOnly = true) 하나다 (보고서 코드 G).
 */
@Configuration(proxyBeanMethods = false)
class DataSourceConfig {

	@Bean(defaultCandidate = false)
	@FlywayDataSource // 마이그레이션은 소스로 (reader 계정은 SELECT만 가능)
	@ConfigurationProperties("app.datasource.writer")
	fun writerDataSource(): HikariDataSource = DataSourceBuilder.create().type(HikariDataSource::class.java).build()

	@Bean(defaultCandidate = false)
	@ConfigurationProperties("app.datasource.reader")
	fun readerDataSource(): HikariDataSource = DataSourceBuilder.create().type(HikariDataSource::class.java).build()

	@Bean
	@Primary
	fun dataSource(
		@Qualifier("writerDataSource") writer: DataSource,
		@Qualifier("readerDataSource") reader: DataSource,
	): DataSource =
		LazyConnectionDataSourceProxy(writer).apply {
			setReadOnlyDataSource(reader)
			setDefaultAutoCommit(true)
			setDefaultTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)
		}
}
