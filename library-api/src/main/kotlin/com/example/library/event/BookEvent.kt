package com.example.library.event

import com.example.library.book.BookType
import java.time.Instant

/**
 * Kafka로 주고받는 메시지. 다른 서비스와의 "계약"이므로 함부로 바꾸면 안 된다.
 *
 * Kotlin에서 주의할 점
 * - 메시지 DTO는 data class가 적합하다 (Entity와 반대).
 * - 역직렬화하는 쪽은 jackson-module-kotlin이 있어야 기본 생성자 없이도 객체를 만들 수 있다.
 * - 필드를 추가할 때는 기본값을 주어야 (val addedLater: String? = null) 예전 메시지도 읽을 수 있다.
 */
data class BookEvent(
	val type: Type,
	val bookId: Long,
	val title: String,
	val bookType: BookType,
	val occurredAt: Instant = Instant.now(),
) {
	enum class Type { CREATED, DELETED }

	companion object {
		const val TOPIC = "book-events"
	}
}
