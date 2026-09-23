package com.example.library.cache

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.BookRepository
import com.example.library.book.BookService
import com.example.library.book.BookType
import com.example.library.book.dto.BookCreateRequest
import com.example.library.book.dto.BookTypeCount
import com.example.library.common.RedisLockService
import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.RedisTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

/**
 * Redis 통합 테스트 (Testcontainers로 진짜 Redis를 띄운다. Docker가 없으면 자동으로 건너뛴다).
 * 캐시 동작, Kotlin data class 직렬화, 분산 락을 확인한다.
 */
@SpringBootTest(properties = ["spring.cache.type=redis"])
@Testcontainers(disabledWithoutDocker = true)
class RedisCacheTest @Autowired constructor(
	private val bookService: BookService,
	private val lockService: RedisLockService,
	private val redisTemplate: RedisTemplate<String, Any>,
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
) {

	companion object {
		@Container
		@ServiceConnection // 컨테이너 주소를 spring.data.redis.* 로 자동 주입
		@JvmStatic
		val redis = RedisContainer("redis:7.4-alpine")
	}

	@AfterEach
	fun clean() {
		redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
		bookRepository.deleteAllInBatch()
		authorRepository.deleteAllInBatch()
	}

	private fun awaitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + timeoutMs
		while (System.currentTimeMillis() < deadline) {
			if (condition()) return
			Thread.sleep(50)
		}
		throw AssertionError("조건이 ${timeoutMs}ms 안에 만족되지 않았습니다")
	}

	@Test
	@DisplayName("@Cacheable: 두 번째 호출은 Redis에서 읽고, 책이 추가되면 캐시가 비워진다")
	fun cacheAndEvict() {
		val authorId = authorRepository.save(Author("김코틀")).id!!
		bookService.create(BookCreateRequest("코틀린", BookType.COMPUTER, 10_000, authorId))

		val first = bookService.countByType() // DB 조회 후 캐시에 저장
		assertThat(redisTemplate.hasKey("bookStats::all")).isTrue()

		val second = bookService.countByType() // 이번엔 Redis에서
		assertThat(second).isEqualTo(first)

		bookService.create(BookCreateRequest("코루틴", BookType.COMPUTER, 10_000, authorId)) // @CacheEvict(allEntries)
		// 주의: allEntries=true 는 "prefix* 패턴으로 찾아 삭제"라서 즉시 반영되지 않을 수 있다.
		// (key를 지정한 evict는 즉시 반영된다) → 테스트에서는 잠시 기다린다.
		awaitUntil { !redisTemplate.hasKey("bookStats::all") }
		assertThat(bookService.countByType().first().count).isEqualTo(2) // 최신 값으로 다시 계산
	}

	@Test
	@DisplayName("Kotlin data class가 JSON으로 저장되고 그대로 복원된다")
	fun dataClassRoundTrip() {
		val value = BookTypeCount(BookType.SCIENCE, 3)

		redisTemplate.opsForValue().set("test:stat", value, Duration.ofMinutes(1))
		val loaded = redisTemplate.opsForValue().get("test:stat")

		assertThat(loaded).isInstanceOf(BookTypeCount::class.java) // LinkedHashMap이 아니라 원래 타입
		assertThat(loaded).isEqualTo(value) // data class라서 값 비교
		assertThat(redisTemplate.getExpire("test:stat")).isPositive() // TTL이 걸려 있다
	}

	@Test
	@DisplayName("분산 락: 먼저 잡은 쪽만 실행하고, 끝나면 풀린다")
	fun distributedLock() {
		val executed = mutableListOf<String>()

		val result = lockService.withLock("book-stat") {
			executed += "첫 번째"
			// 락을 잡은 동안 다른 서버가 시도하면 null (실행되지 않음)
			val inner = lockService.withLock("book-stat") { executed += "두 번째"; "실행됨" }
			assertThat(inner).isNull()
			"완료"
		}

		assertThat(result).isEqualTo("완료")
		assertThat(executed).containsExactly("첫 번째")
		assertThat(redisTemplate.hasKey("lock:book-stat")).isFalse() // 끝나면 해제
		assertThat(lockService.withLock("book-stat") { "다시 잡음" }).isEqualTo("다시 잡음")
	}
}
