package com.example.library.book

import com.example.library.author.Author
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 순수 단위 테스트: 스프링도 DB도 띄우지 않는다 → 수 ms.
 * 도메인 규칙(검증, 상태 변경, 연관관계 편의 메소드)은 여기서 가장 많이 검증한다.
 */
class BookTest {

	@Test
	fun `제목이 비어 있으면 생성할 수 없다`() {
		assertThrows<IllegalArgumentException> { Book("  ", BookType.COMPUTER, 1_000, Author("A")) }
	}

	@Test
	fun `가격이 음수면 수정할 수 없다`() {
		val book = Author("A").writeBook("책", BookType.COMPUTER, 1_000)

		assertThrows<IllegalArgumentException> { book.update("책", BookType.COMPUTER, -1) }
	}

	@Test
	fun `writeBook은 양방향 연관관계를 한 번에 맞춘다`() {
		val author = Author("A")

		val book = author.writeBook("책", BookType.COMPUTER, 1_000)

		assertThat(book.author).isSameAs(author)
		assertThat(author.books).containsExactly(book)
	}

	@Test
	fun `작가를 바꾸면 이전 작가 목록에서 빠지고 새 작가 목록에 들어간다`() {
		val before = Author("A")
		val after = Author("B")
		val book = before.writeBook("책", BookType.COMPUTER, 1_000)

		book.changeAuthor(after)

		assertThat(before.books).isEmpty()
		assertThat(after.books).containsExactly(book)
		assertThat(book.author).isSameAs(after)
	}
}
