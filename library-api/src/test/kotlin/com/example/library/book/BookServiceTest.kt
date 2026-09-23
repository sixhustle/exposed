package com.example.library.book

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.dto.BookCreateRequest
import com.example.library.book.dto.BookSearchCondition
import com.example.library.book.dto.BookUpdateRequest
import com.example.library.common.exception.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

@SpringBootTest
class BookServiceTest @Autowired constructor( // 테스트 클래스는 생성자 주입에 @Autowired 명시
	private val bookService: BookService,
	private val bookRepository: BookRepository,
	private val authorRepository: AuthorRepository,
) {

	@AfterEach
	fun clean() {
		bookRepository.deleteAllInBatch() // FK 때문에 자식(Book) → 부모(Author) 순서로 지운다
		authorRepository.deleteAllInBatch()
	}

	@Test
	@DisplayName("책을 등록하면 작가 정보와 함께 조회된다")
	fun createAndGet() {
		val author = authorRepository.save(Author("김코틀"))

		val created = bookService.create(BookCreateRequest("코틀린 입문", BookType.COMPUTER, 20_000, author.id!!))
		val found = bookService.get(created.id)

		assertThat(found.title).isEqualTo("코틀린 입문")
		assertThat(found.authorName).isEqualTo("김코틀")
		assertThat(found.createdAt).isNotNull()
	}

	@Test
	@DisplayName("없는 책을 조회하면 NotFoundException")
	fun getNotFound() {
		assertThrows<NotFoundException> { bookService.get(999L) }
	}

	@Test
	@DisplayName("수정은 save 없이 변경 감지로 반영되고, 작가도 바꿀 수 있다")
	fun update() {
		val a = authorRepository.save(Author("A"))
		val b = authorRepository.save(Author("B"))
		val book = bookService.create(BookCreateRequest("원래 제목", BookType.ECONOMY, 10_000, a.id!!))

		bookService.update(book.id, BookUpdateRequest("바뀐 제목", BookType.SCIENCE, 15_000, authorId = b.id))

		val updated = bookService.get(book.id)
		assertThat(updated.title).isEqualTo("바뀐 제목")
		assertThat(updated.type).isEqualTo(BookType.SCIENCE)
		assertThat(updated.authorName).isEqualTo("B")
	}

	@Test
	@DisplayName("삭제하면 다시 조회할 수 없다")
	fun delete() {
		val author = authorRepository.save(Author("A"))
		val book = bookService.create(BookCreateRequest("지울 책", BookType.LANGUAGE, 5_000, author.id!!))

		bookService.delete(book.id)

		assertThat(bookRepository.existsById(book.id)).isFalse()
		assertThat(authorRepository.existsById(author.id!!)).isTrue()
	}

	@Test
	@DisplayName("검색: 넘긴 조건만 AND로 적용되고 페이징된다")
	fun search() {
		val kim = authorRepository.save(Author("김"))
		val lee = authorRepository.save(Author("이"))
		listOf(
			Triple("코틀린 인 액션", BookType.COMPUTER, 36_000),
			Triple("코틀린 코루틴", BookType.COMPUTER, 30_000),
			Triple("경제 기초", BookType.ECONOMY, 18_000),
		).forEach { (title, type, price) -> bookService.create(BookCreateRequest(title, type, price, kim.id!!)) }
		bookService.create(BookCreateRequest("코틀린 스프링", BookType.COMPUTER, 40_000, lee.id!!))

		val byTitle = bookService.search(BookSearchCondition(title = "코틀린"), PageRequest.of(0, 2))
		assertThat(byTitle.totalElements).isEqualTo(3)
		assertThat(byTitle.content).hasSize(2) // size=2 로 잘림
		assertThat(byTitle.totalPages).isEqualTo(2)

		val combined = bookService.search(
			BookSearchCondition(title = "코틀린", authorName = "김", maxPrice = 32_000),
			PageRequest.of(0, 10),
		)
		assertThat(combined.content).extracting("title").containsExactly("코틀린 코루틴")
	}

	@Test
	@DisplayName("분야별 권수를 GROUP BY로 집계한다")
	fun countByType() {
		val author = authorRepository.save(Author("A"))
		bookService.create(BookCreateRequest("1", BookType.COMPUTER, 1, author.id!!))
		bookService.create(BookCreateRequest("2", BookType.COMPUTER, 1, author.id!!))
		bookService.create(BookCreateRequest("3", BookType.SCIENCE, 1, author.id!!))

		val result = bookService.countByType().associate { it.type to it.count }

		assertThat(result).containsEntry(BookType.COMPUTER, 2L).containsEntry(BookType.SCIENCE, 1L).hasSize(2)
	}
}
