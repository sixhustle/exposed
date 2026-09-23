package com.example.library.learning

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 학습용: 코루틴의 기본 동작을 테스트로 확인한다.
 *
 * runTest { } 는 "가상 시간"을 쓴다. delay(1000)이 실제로 1초를 기다리지 않고 시계만 1000ms 앞으로 돌린다.
 * 그래서 시간이 걸리는 코루틴 코드도 테스트는 순식간에 끝난다. currentTime 으로 가상 시각을 읽을 수 있다.
 */
class CoroutineBasicsTest {

	// suspend 함수: "잠시 멈출 수 있는 함수". delay 같은 다른 suspend 함수를 부를 수 있다.
	private suspend fun fetchPrice(): Int {
		delay(1_000) // 1초 걸리는 외부 API 호출이라고 생각하자. 기다리는 동안 스레드는 다른 일을 한다
		return 20_000
	}

	@Test
	fun `suspend 함수는 순서대로 부르면 시간이 더해진다`() = runTest {
		val a = fetchPrice()
		val b = fetchPrice()

		assertThat(a + b).isEqualTo(40_000)
		assertThat(currentTime).isEqualTo(2_000) // 1초 + 1초
	}

	@Test
	fun `async로 동시에 부르면 가장 오래 걸리는 것만큼만 걸린다`() = runTest {
		val a = async { fetchPrice() } // 시작만 하고 바로 다음 줄로
		val b = async { fetchPrice() }

		assertThat(a.await() + b.await()).isEqualTo(40_000) // await: 결과가 나올 때까지 기다림
		assertThat(currentTime).isEqualTo(1_000) // 동시에 진행 → 1초
	}

	@Test
	fun `launch는 결과 없이 일을 시작하고, 부모는 자식이 끝날 때까지 기다린다`() = runTest {
		val log = mutableListOf<String>()

		coroutineScope {
			launch { delay(200); log += "자식 2" }
			launch { delay(100); log += "자식 1" }
			log += "부모: 자식들 시작시킴"
		} // coroutineScope 는 안에서 시작한 자식이 모두 끝나야 빠져나온다 (구조적 동시성)

		assertThat(log).containsExactly("부모: 자식들 시작시킴", "자식 1", "자식 2")
	}

	@Test
	fun `자식 하나가 실패하면 형제는 취소되고 예외는 부모로 전달된다`() = runTest {
		val sibling = CompletableDeferred<String>()

		assertThrows<IllegalStateException> {
			coroutineScope {
				launch {
					try {
						delay(10_000)
						sibling.complete("끝까지 실행됨")
					} finally {
						if (!sibling.isCompleted) sibling.complete("취소됨")
					}
				}
				launch {
					delay(100)
					throw IllegalStateException("재고 조회 실패")
				}
			}
		}

		assertThat(sibling.await()).isEqualTo("취소됨") // 실패가 번지면 기다리던 형제도 정리된다
	}

	@Test
	fun `10만 개의 코루틴도 가볍게 돌린다`() = runTest {
		var done = 0
		coroutineScope {
			repeat(100_000) {
				launch {
					delay(1_000)
					done++
				}
			}
		}
		assertThat(done).isEqualTo(100_000) // 스레드 10만 개였다면 메모리가 버티지 못한다
	}

	@Test
	fun `withContext로 실행할 스레드 풀을 바꾼다`() = runTest {
		val caller = Thread.currentThread().name

		val worker = withContext(Dispatchers.IO) { Thread.currentThread().name } // 블로킹 작업(JDBC, 파일)용

		assertThat(worker).isNotEqualTo(caller)
		assertThat(worker).contains("DefaultDispatcher-worker")
	}
}
