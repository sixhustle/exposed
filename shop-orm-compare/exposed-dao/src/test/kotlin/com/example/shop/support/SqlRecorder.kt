package com.example.shop.support

import org.springframework.beans.factory.config.BeanPostProcessor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import javax.sql.DataSource

/**
 * JDBC 수준에서 실제로 실행된 SQL을 기록한다. ORM과 무관한 같은 기준(JDBC 호출)으로 세 프로젝트의 SQL 수를 비교하려고, 세 프로젝트에 같은 동작의 기록기를 둔다.
 * (Hibernate Statistics나 Exposed StatementInterceptor는 각자 기준이 달라 비교가 어렵다)
 *
 * - 애플리케이션이 쓰는 "dataSource" 빈(라우팅 프록시)을 감싼다. Flyway가 쓰는 writerDataSource는 감싸지 않는다.
 * - execute* / executeBatch 호출 1번 = 1건. JDBC 배치는 executeBatch 1건으로 센다 (DB 왕복 기준)
 * - [record] 블록 안에서, 같은 스레드에서 실행된 것만 모은다
 */
object SqlRecorder {
	private val current = ThreadLocal<MutableList<String>?>()

	fun <T> record(block: () -> T): Pair<T, List<String>> {
		val sqls = mutableListOf<String>()
		current.set(sqls)
		try {
			return block() to sqls.toList()
		} finally {
			current.remove()
		}
	}

	internal fun add(sql: String) {
		current.get()?.add(sql.trim().replace(Regex("\\s+"), " "))
	}

	fun wrap(dataSource: DataSource): DataSource = proxy(dataSource, DataSource::class.java) { target, method, args ->
		val result = invoke(target, method, args)
		if (result is Connection) wrapConnection(result) else result
	}

	private fun wrapConnection(connection: Connection): Connection = proxy(connection, Connection::class.java) { target, method, args ->
		val result = invoke(target, method, args)
		when {
			result is PreparedStatement && method.name.startsWith("prepare") -> wrapPrepared(result, args!![0] as String)
			result is Statement && method.name == "createStatement" -> wrapStatement(result)
			else -> result
		}
	}

	private fun wrapPrepared(statement: PreparedStatement, sql: String): PreparedStatement =
		proxy(statement, PreparedStatement::class.java) { target, method, args ->
			if (method.name.startsWith("execute") && (args == null || args.isEmpty())) add(sql)
			invoke(target, method, args)
		}

	private fun wrapStatement(statement: Statement): Statement = proxy(statement, Statement::class.java) { target, method, args ->
		if (method.name.startsWith("execute") && args != null && args.isNotEmpty() && args[0] is String) add(args[0] as String)
		invoke(target, method, args)
	}

	private fun invoke(target: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? =
		try {
			method.invoke(target, *(args ?: emptyArray()))
		} catch (e: InvocationTargetException) {
			throw e.targetException
		}

	@Suppress("UNCHECKED_CAST")
	private fun <T : Any> proxy(target: T, type: Class<T>, handler: (T, java.lang.reflect.Method, Array<out Any?>?) -> Any?): T =
		Proxy.newProxyInstance(type.classLoader, arrayOf(type), InvocationHandler { _, method, args -> handler(target, method, args) }) as T
}

/** 계약 테스트 컨텍스트에서 dataSource 빈을 SqlRecorder로 감싼다 */
class SqlRecordingPostProcessor : BeanPostProcessor {
	override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
		if (beanName == "dataSource" && bean is DataSource) SqlRecorder.wrap(bean) else bean
}
