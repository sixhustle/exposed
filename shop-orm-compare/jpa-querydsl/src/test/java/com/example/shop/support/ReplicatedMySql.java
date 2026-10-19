package com.example.shop.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 테스트용 MySQL 소스 + 레플리카 (GTID 복제). ../docker/compose.yaml 과 같은 my.cnf·초기화 스크립트를 그대로 쓴다.
 * 테스트 JVM당 한 번만 뜬다 (싱글톤 컨테이너).
 *
 * 레플리카는 비동기 복제라 "소스에 쓰고 곧바로 레플리카에서 읽기"가 실패할 수 있다.
 * 테스트는 쓰기 뒤 awaitReplica()로 레플리카가 따라잡을 때까지 기다린 다음 readOnly 조회를 한다.
 */
public final class ReplicatedMySql {

    public static final String READER_USER = "shop_reader";
    public static final String READER_PASSWORD = "shop_reader";
    private static final String ROOT_PASSWORD = "shop"; // MySQLContainer는 MYSQL_ROOT_PASSWORD를 사용자 비밀번호와 같게 둔다

    private static final Path DOCKER_DIR = Path.of(System.getProperty("compare.docker-dir"));
    private static final Network NETWORK = Network.newNetwork();

    public static final MySQLContainer SOURCE = mysql("mysql-source")
            .withCopyFileToContainer(file("mysql/source/my.cnf"), "/etc/mysql/conf.d/replication.cnf")
            .withCopyFileToContainer(file("mysql/common/01-reader-user.sql"), "/docker-entrypoint-initdb.d/01-reader-user.sql")
            .withCopyFileToContainer(file("mysql/source/02-replication-user.sql"), "/docker-entrypoint-initdb.d/02-replication-user.sql")
            .withCopyFileToContainer(file("mysql/source/99-reset-binlog.sql"), "/docker-entrypoint-initdb.d/99-reset-binlog.sql");

    public static final MySQLContainer REPLICA = mysql("mysql-replica")
            .withCopyFileToContainer(file("mysql/replica/my.cnf"), "/etc/mysql/conf.d/replication.cnf")
            .withCopyFileToContainer(file("mysql/common/01-reader-user.sql"), "/docker-entrypoint-initdb.d/01-reader-user.sql")
            .withCopyFileToContainer(file("mysql/replica/02-start-replica.sql"), "/docker-entrypoint-initdb.d/02-start-replica.sql");

    static {
        SOURCE.start();
        REPLICA.start();
    }

    private ReplicatedMySql() {
    }

    public static String writerJdbcUrl() {
        String url = SOURCE.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
    }

    public static String readerJdbcUrl() {
        return REPLICA.getJdbcUrl();
    }

    /** 소스에서 지금까지 커밋된 GTID 집합을 레플리카가 모두 적용할 때까지 기다린다 */
    public static void awaitReplica() {
        try (Connection source = root(SOURCE); Connection replica = root(REPLICA)) {
            String executed;
            try (Statement st = source.createStatement(); ResultSet rs = st.executeQuery("SELECT @@GLOBAL.gtid_executed")) {
                rs.next();
                executed = rs.getString(1);
            }
            try (PreparedStatement ps = replica.prepareStatement("SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)")) {
                ps.setString(1, executed);
                ps.setInt(2, 10);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) != 0) throw new IllegalStateException("레플리카가 10초 안에 소스를 따라잡지 못했습니다 (gtid=" + executed + ")");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 소스에서 테이블을 비운다. TRUNCATE도 복제되므로 레플리카도 비워진다 */
    public static void truncateAll() {
        try (Connection c = root(SOURCE); Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : List.of("order_items", "orders", "items", "users")) st.execute("TRUNCATE TABLE shop." + table);
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        awaitReplica();
    }

    /** 소스에 root로 직접 실행 (테스트 데이터 조작용) */
    public static void executeOnSource(String sql, Object... args) {
        try (Connection c = root(SOURCE); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Connection root(MySQLContainer container) throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), "root", ROOT_PASSWORD);
    }

    private static MySQLContainer mysql(String alias) {
        return new MySQLContainer("mysql:8.4")
                .withNetwork(NETWORK)
                .withNetworkAliases(alias)
                .withDatabaseName("shop")
                .withUsername("shop")
                .withPassword("shop")
                .withEnv("TZ", "UTC");
    }

    private static MountableFile file(String relative) {
        return MountableFile.forHostPath(DOCKER_DIR.resolve(relative));
    }
}
