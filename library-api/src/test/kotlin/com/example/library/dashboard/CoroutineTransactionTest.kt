package com.example.library.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 코루틴 + JPA 함정: 스프링 트랜잭션은 스레드(ThreadLocal)에 묶여 있다.
 * 트랜잭션 안에서 withContext(Dispatchers.IO) 로 다른 스레드로 넘어가면 그 안에서는 트랜잭션이 없다.
 */
@SpringBootTest
class CoroutineTransactionTest @Autowired constructor(
	private val tx: TransactionTemplate,
) {

	@Test
	fun `트랜잭션 안이라도 다른 스레드로 넘어가면 트랜잭션이 보이지 않는다`() {
		var outer = false
		var inner = true

		tx.executeWithoutResult {
			outer = TransactionSynchronizationManager.isActualTransactionActive()
			inner = runBlocking {
				withContext(Dispatchers.IO) { TransactionSynchronizationManager.isActualTransactionActive() }
			}
		}

		assertThat(outer).isTrue() // 원래 스레드: 트랜잭션 있음
		assertThat(inner).isFalse() // IO 스레드: 트랜잭션 없음 → 여기서 한 변경은 롤백/변경 감지 대상이 아니다
	}
}
