package com.example.shop.common.exposed

import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.sql.SQLException

/**
 * @Repository 빈에서 던진 ExposedSQLException(checked SQLException)을 Spring DataAccessException으로 번역한다.
 * JPA는 Spring이 해 주지만 Exposed starter에는 없다 (보고서 05).
 *
 * setBeforeExistingAdvisors(false): advisor 목록의 앞 = 바깥이다. false로 기존 advisor(트랜잭션) "뒤 = 안쪽"에 붙여
 * 같은 빈에 @Transactional이 있어도 트랜잭션이 번역된 예외를 보게 한다 (true면 noRollbackFor가 무시됨, 보고서 08-3 측정).
 */
@Component
class ExposedExceptionTranslationPostProcessor : AbstractBeanFactoryAwareAdvisingPostProcessor() {
	init {
		advisor = DefaultPointcutAdvisor(
			AnnotationMatchingPointcut(Repository::class.java, true),
			ExposedExceptionTranslationInterceptor(),
		)
		setBeforeExistingAdvisors(false)
	}
}

class ExposedExceptionTranslationInterceptor : MethodInterceptor {
	private val translator = SQLExceptionSubclassTranslator()

	override fun invoke(invocation: MethodInvocation): Any? =
		try {
			invocation.proceed()
		} catch (e: ExposedSQLException) {
			val sqlException = e.cause as? SQLException ?: e
			throw translator.translate(invocation.method.toGenericString(), null, sqlException) ?: e
		}
}
