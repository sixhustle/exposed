package com.example.library.event

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.BookRepository
import com.example.library.book.BookService
import com.example.library.book.BookType
import com.example.library.book.dto.BookCreateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

/**
 * Kafka 통합 테스트. @EmbeddedKafka가 테스트용 브로커를 JVM 안에 띄우므로 Docker가 없어도 된다.
 * 실제 발행 → 수신, 커밋 후 발행, 실패 시 DLT 이동까지 확인한다.
 */
@SpringBootTest(
	properties = [
		"spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
		"app.kafka.enabled=true",
		"app.kafka.listener-enabled=true",
	],
)
@EmbeddedKafka(partitions = 1, topics = [BookEvent.TOPIC])
class KafkaEventTest @Autowired constructor(
	private val bookService: BookService,
	private val consumer: BookEventConsumer,
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
	private val tx: TransactionTemplate,
) {

	@AfterEach
	fun clean() {
		consumer.handled.clear()
		consumer.deadLettered.clear()
		consumer.failFor = null
		bookRepository.deleteAllInBatch()
		authorRepository.deleteAllInBatch()
	}

	private fun <T> waitFor(timeout: Duration = Duration.ofSeconds(20), condition: () -> T?): T {
		val deadline = Instant.now().plus(timeout)
		while (Instant.now() < deadline) {
			condition()?.let { return it }
			Thread.sleep(100)
		}
		throw AssertionError("조건이 ${timeout.seconds}초 안에 만족되지 않았습니다")
	}

	@Test
	@DisplayName("책을 등록하면 CREATED 이벤트가 발행되고 컨슈머가 받는다")
	fun publishAndConsume() {
		val authorId = authorRepository.save(Author("김코틀")).id!!

		val created = bookService.create(BookCreateRequest("코틀린 입문", BookType.COMPUTER, 20_000, authorId))

		val received = waitFor { consumer.handled[created.id] }
		assertThat(received.type).isEqualTo(BookEvent.Type.CREATED)
		assertThat(received.title).isEqualTo("코틀린 입문")
		assertThat(received.bookType).isEqualTo(BookType.COMPUTER) // Enum · data class가 JSON 왕복 후에도 그대로
	}

	@Test
	@DisplayName("트랜잭션이 롤백되면 이벤트는 발행되지 않는다 (AFTER_COMMIT)")
	fun notPublishedOnRollback() {
		val authorId = authorRepository.save(Author("김코틀")).id!!

		assertThrows<IllegalStateException> {
			tx.executeWithoutResult {
				bookService.create(BookCreateRequest("취소될 책", BookType.COMPUTER, 1_000, authorId))
				throw IllegalStateException("이후 로직에서 실패") // 롤백
			}
		}

		Thread.sleep(1_000) // 혹시라도 발행됐는지 볼 시간을 준다
		assertThat(consumer.handled).isEmpty()
	}

	@Test
	@DisplayName("처리에 계속 실패하면 재시도 후 DLT로 이동한다")
	fun deadLetterTopic() {
		val authorId = authorRepository.save(Author("김코틀")).id!!
		val created = bookService.create(BookCreateRequest("실패할 책", BookType.SCIENCE, 1_000, authorId))
		consumer.failFor = created.id // 이 책의 이벤트는 처리 실패하도록

		val topic = waitFor { consumer.deadLettered[created.id] }

		assertThat(topic).isEqualTo(BookEvent.TOPIC)
		assertThat(consumer.handled).doesNotContainKey(created.id)
	}
}
