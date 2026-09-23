package com.example.library.event

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * DB 커밋이 끝난 뒤에 Kafka로 발행한다.
 *
 * 왜 이렇게 하나?
 * - 서비스에서 바로 send() 하면, 이후 트랜잭션이 롤백되어도 메시지는 이미 나가 버린다
 *   ("책 등록 실패했는데 등록 이벤트가 나간" 상황).
 * - 그래서 서비스는 스프링 애플리케이션 이벤트만 발행하고,
 *   여기서 @TransactionalEventListener(AFTER_COMMIT)로 받아 커밋 후에만 Kafka로 보낸다.
 * - 반대로 "커밋은 됐는데 Kafka 발행이 실패"하는 경우까지 막으려면 Outbox 패턴을 쓴다
 *   (이벤트를 같은 트랜잭션에서 DB 테이블에 저장하고, 별도 프로세스가 읽어 발행).
 */
@Component
// 카프카가 없는 환경(일반 테스트)에서는 이 빈을 만들지 않는다
@ConditionalOnProperty(name = ["app.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class BookEventPublisher(
	private val kafkaTemplate: KafkaTemplate<String, BookEvent>,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun publish(event: BookEvent) {
		// 같은 책의 이벤트는 같은 파티션으로 → 순서가 보장된다 (키 = bookId)
		kafkaTemplate.send(BookEvent.TOPIC, event.bookId.toString(), event)
			.whenComplete { result, ex ->
				if (ex != null) log.error("이벤트 발행 실패: {}", event, ex)
				else log.info("이벤트 발행: {} partition={}", event.type, result.recordMetadata.partition())
			}
	}
}
