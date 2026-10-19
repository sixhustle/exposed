package com.example.shop.support

import org.testcontainers.containers.Network
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.sql.DriverManager

/**
 * 테스트용 MySQL 소스 + 레플리카 (GTID 복제). docker/compose.yaml 과 같은 my.cnf·초기화 스크립트를 그대로 쓴다.
 * 테스트 JVM당 한 번만 뜬다 (싱글톤 컨테이너).
 *
 * 레플리카는 비동기 복제라 "소스에 쓰고 곧바로 레플리카에서 읽기"가 실패할 수 있다.
 * 테스트는 쓰기 뒤 [awaitReplica]로 레플리카가 따라잡을 때까지 기다린 다음 readOnly 조회를 한다.
 */
object ReplicatedMySql {
	const val READER_USER = "shop_reader"
	const val READER_PASSWORD = "shop_reader"
	private const val ROOT_PASSWORD = "shop" // MySQLContainer는 MYSQL_ROOT_PASSWORD를 사용자 비밀번호와 같게 둔다

	private val dockerDir: Path = Path.of(
		System.getProperty("compare.docker-dir") ?: error("compare.docker-dir 시스템 프로퍼티가 없습니다 (build.gradle.kts)"),
	)
	private val network: Network = Network.newNetwork()

	val source: MySQLContainer = mysql("mysql-source")
		.withCopyFileToContainer(file("mysql/source/my.cnf"), "/etc/mysql/conf.d/replication.cnf")
		.withCopyFileToContainer(file("mysql/common/01-reader-user.sql"), "/docker-entrypoint-initdb.d/01-reader-user.sql")
		.withCopyFileToContainer(file("mysql/source/02-replication-user.sql"), "/docker-entrypoint-initdb.d/02-replication-user.sql")
		.withCopyFileToContainer(file("mysql/source/99-reset-binlog.sql"), "/docker-entrypoint-initdb.d/99-reset-binlog.sql")
		.also { it.start() }

	val replica: MySQLContainer = mysql("mysql-replica")
		.withCopyFileToContainer(file("mysql/replica/my.cnf"), "/etc/mysql/conf.d/replication.cnf")
		.withCopyFileToContainer(file("mysql/common/01-reader-user.sql"), "/docker-entrypoint-initdb.d/01-reader-user.sql")
		.withCopyFileToContainer(file("mysql/replica/02-start-replica.sql"), "/docker-entrypoint-initdb.d/02-start-replica.sql")
		.also { it.start() }

	fun writerJdbcUrl(): String = source.jdbcUrl + (if ("?" in source.jdbcUrl) "&" else "?") + "rewriteBatchedStatements=true"

	fun readerJdbcUrl(): String = replica.jdbcUrl

	/** 소스에서 지금까지 커밋된 GTID 집합을 레플리카가 모두 적용할 때까지 기다린다 */
	fun awaitReplica(timeoutSeconds: Int = 10) {
		val executed = DriverManager.getConnection(source.jdbcUrl, "root", ROOT_PASSWORD).use { c ->
			c.createStatement().executeQuery("SELECT @@GLOBAL.gtid_executed").use { rs -> rs.next(); rs.getString(1) }
		}
		DriverManager.getConnection(replica.jdbcUrl, "root", ROOT_PASSWORD).use { c ->
			c.prepareStatement("SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)").use { ps ->
				ps.setString(1, executed)
				ps.setInt(2, timeoutSeconds)
				ps.executeQuery().use { rs ->
					rs.next()
					check(rs.getInt(1) == 0) { "레플리카가 ${timeoutSeconds}초 안에 소스를 따라잡지 못했습니다 (gtid=$executed)" }
				}
			}
		}
	}

	/** 소스에서 테이블을 비운다. TRUNCATE도 복제되므로 레플리카도 비워진다 */
	fun truncateAll() {
		DriverManager.getConnection(source.jdbcUrl, "root", ROOT_PASSWORD).use { c ->
			c.createStatement().use { st ->
				st.execute("SET FOREIGN_KEY_CHECKS = 0")
				listOf("order_items", "orders", "items", "users").forEach { st.execute("TRUNCATE TABLE shop.$it") }
				st.execute("SET FOREIGN_KEY_CHECKS = 1")
			}
		}
		awaitReplica()
	}

	private fun mysql(alias: String): MySQLContainer =
		MySQLContainer("mysql:8.4")
			.withNetwork(network)
			.withNetworkAliases(alias)
			.withDatabaseName("shop")
			.withUsername("shop")
			.withPassword("shop")
			.withEnv("TZ", "UTC")

	private fun file(relative: String): MountableFile = MountableFile.forHostPath(dockerDir.resolve(relative))
}
