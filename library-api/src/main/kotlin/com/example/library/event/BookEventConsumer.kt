package com.example.library.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.kafka.annotation.BackOff
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 컨슈머. 처리에 실패하면 재시도 토픽 → 그래도 실패하면 DLT(Dead Letter Topic)로 보낸다.
 *
 * - @RetryableTopic: 실패한 메시지를 book-events-retry-0 토픽으로 보내 잠시 뒤 다시 처리한다.
 *   메인 컨슈머가 막히지 않는 것이 장점이다(블로킹 재시도와의 차이).
 * - 소비는 "여러 번 도착할 수 있다"고 가정하고 멱등하게 처리한다 (at-least-once).
 */
@Component
class BookEventConsumer {

	private val log = LoggerFactory.getLogger(javaClass)

	/** 예제용 처리 결과 저장소 (실제라면 DB나 외부 시스템) */
	val handled = ConcurrentHashMap<Long, BookEvent>()
	val deadLettered = ConcurrentHashMap<Long, String>()

	/** 테스트에서 실패 상황을 만들기 위한 스위치 */
	@Volatile
	var failFor: Long? = null

	@RetryableTopic(
		attempts = "2", // 최초 1회 + 재시도 1회
		backOff = BackOff(delay = 200), // 0.2초 뒤 재시도. 재시도 토픽 이름은 기본 규칙(book-events-retry)을 따른다
	)
	@KafkaListener(
		topics = [BookEvent.TOPIC],
		groupId = "library-book-events",
		containerFactory = "bookEventListenerContainerFactory",
	)
	fun consume(event: BookEvent, @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int) {
		log.info("수신: {} partition={}", event, partition)
		if (event.bookId == failFor) throw IllegalStateException("처리 실패 흉내: ${event.bookId}")
		handled[event.bookId] = event // 멱등: 같은 이벤트가 또 와도 결과가 같다
	}

	@DltHandler
	fun dlt(event: BookEvent, @Header(KafkaHeaders.ORIGINAL_TOPIC) topic: String) {
		log.warn("DLT로 이동: {} (원래 토픽 {})", event, topic)
		deadLettered[event.bookId] = topic // 실제라면 알림을 보내거나 수동 처리 큐에 쌓는다
	}
}
