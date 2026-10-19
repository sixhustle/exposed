package com.example.shop.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * JDBC 수준에서 실제로 실행된 SQL을 기록한다. ORM과 무관한 같은 기준(JDBC 호출)으로 세 프로젝트의 SQL 수를 비교하려고,
 * 세 프로젝트에 같은 동작의 기록기를 둔다. (Hibernate Statistics나 Exposed StatementInterceptor는 기준이 서로 다르다)
 *
 * - 애플리케이션이 쓰는 "dataSource" 빈(라우팅 프록시)을 감싼다. Flyway가 쓰는 writerDataSource는 감싸지 않는다.
 * - execute* / executeBatch 호출 1번 = 1건. JDBC 배치는 executeBatch 1건으로 센다 (DB 왕복 기준)
 * - record 블록 안에서, 같은 스레드에서 실행된 것만 모은다
 */
public final class SqlRecorder {

    private static final ThreadLocal<List<String>> CURRENT = new ThreadLocal<>();

    private SqlRecorder() {
    }

    public record Recorded<T>(T result, List<String> sql) {
    }

    public static <T> Recorded<T> record(Supplier<T> block) {
        List<String> sqls = new ArrayList<>();
        CURRENT.set(sqls);
        try {
            T result = block.get();
            return new Recorded<>(result, List.copyOf(sqls));
        } finally {
            CURRENT.remove();
        }
    }

    static void add(String sql) {
        List<String> sqls = CURRENT.get();
        if (sqls != null) sqls.add(sql.trim().replaceAll("\\s+", " "));
    }

    public static DataSource wrap(DataSource dataSource) {
        return proxy(dataSource, DataSource.class, (target, method, args) -> {
            Object result = invoke(target, method, args);
            return result instanceof Connection c ? wrapConnection(c) : result;
        });
    }

    private static Connection wrapConnection(Connection connection) {
        return proxy(connection, Connection.class, (target, method, args) -> {
            Object result = invoke(target, method, args);
            if (result instanceof PreparedStatement ps && method.getName().startsWith("prepare")) return wrapPrepared(ps, (String) args[0]);
            if (result instanceof Statement st && method.getName().equals("createStatement")) return wrapStatement(st);
            return result;
        });
    }

    private static PreparedStatement wrapPrepared(PreparedStatement statement, String sql) {
        return proxy(statement, PreparedStatement.class, (target, method, args) -> {
            if (method.getName().startsWith("execute") && (args == null || args.length == 0)) add(sql);
            return invoke(target, method, args);
        });
    }

    private static Statement wrapStatement(Statement statement) {
        return proxy(statement, Statement.class, (target, method, args) -> {
            if (method.getName().startsWith("execute") && args != null && args.length > 0 && args[0] instanceof String sql) add(sql);
            return invoke(target, method, args);
        });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private interface Handler<T> {
        Object handle(T target, Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(T target, Class<T> type, Handler<T> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> handler.handle(target, method, args));
    }

    /** 계약 테스트 컨텍스트에서 dataSource 빈을 SqlRecorder로 감싼다 */
    public static class PostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            return beanName.equals("dataSource") && bean instanceof DataSource ds ? wrap(ds) : bean;
        }
    }
}
