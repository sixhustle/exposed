package com.example.library.book

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.dto.BookCreateRequest
import com.example.library.book.dto.BookSearchCondition
import com.example.library.book.dto.BookSummary
import com.example.library.book.dto.BookUpdateRequest
import com.example.library.common.exception.NotFoundException
import io.mockk.every
import org.springframework.context.ApplicationEventPublisher
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

/**
 * Mock 단위 테스트 (MockK): 스프링 컨텍스트 없이 Service 로직만 검증한다.
 * - 의존성(Repository)은 가짜 객체로 바꾸고, "무엇을 호출했고 무엇을 돌려주는지"만 본다.
 * - 스프링을 띄우지 않으므로 ApplicationContext 캐시와 무관하다 (공유할 컨텍스트 자체가 없다).
 * - 실제 SQL·트랜잭션·변경 감지는 검증하지 못한다 → 그건 @DataJpaTest / @SpringBootTest 몫.
 */
class BookServiceMockKTest {

	private val bookRepository = mockk<BookRepository>()
	private val bookQueryRepository = mockk<BookQueryRepository>()
	private val authorRepository = mockk<AuthorRepository>()
	// 스프링 이벤트 발행기도 가짜로: relaxed = true 면 stub하지 않은 호출을 조용히 넘긴다
	private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

	// 테스트 대상은 생성자에 가짜 객체를 직접 넣어 만든다 (스프링 주입 없음)
	private val bookService = BookService(bookRepository, bookQueryRepository, authorRepository, eventPublisher)

	@Test
	fun `책을 등록하면 작가를 찾아 연결한 뒤 저장한다`() {
		val author = Author("김코틀", id = 1L)
		// findByIdOrNull/findByIdOrThrow는 확장 함수라 stub할 수 없다 → 내부에서 호출하는 findById를 stub
		every { authorRepository.findById(1L) } returns Optional.of(author)
		// save에 넘어온 Book에 DB가 id를 채워준 것처럼 흉내 낸다
		every { bookRepository.save(any()) } answers {
			firstArg<Book>().let { Book(it.title, it.type, it.price, it.author, id = 10L) }
		}

		val response = bookService.create(BookCreateRequest("코틀린", BookType.COMPUTER, 20_000, 1L))

		assertThat(response.id).isEqualTo(10L)
		assertThat(response.authorName).isEqualTo("김코틀")
		assertThat(author.books.map { it.title }).containsExactly("코틀린") // 편의 메소드로 연결됨
		verify(exactly = 1) { bookRepository.save(any()) }
	}

	@Test
	fun `없는 작가로 등록하면 NotFoundException이고 저장하지 않는다`() {
		every { authorRepository.findById(99L) } returns Optional.empty()

		assertThrows<NotFoundException> {
			bookService.create(BookCreateRequest("코틀린", BookType.COMPUTER, 20_000, 99L))
		}
		verify(exactly = 0) { bookRepository.save(any()) }
	}

	@Test
	fun `없는 책을 수정하면 NotFoundException`() {
		every { bookRepository.findWithAuthorById(1L) } returns null

		assertThrows<NotFoundException> {
			bookService.update(1L, BookUpdateRequest("제목", BookType.COMPUTER, 1_000))
		}
	}

	@Test
	fun `수정은 엔티티의 값을 바꾸기만 하고 save를 호출하지 않는다`() {
		val book = Book("원래", BookType.ECONOMY, 1_000, Author("김", id = 1L), id = 10L)
		every { bookRepository.findWithAuthorById(10L) } returns book

		bookService.update(10L, BookUpdateRequest("바뀜", BookType.SCIENCE, 2_000))

		assertThat(book.title).isEqualTo("바뀜")
		verify(exactly = 0) { bookRepository.save(any()) } // 실제 UPDATE는 변경 감지가 담당
	}

	@Test
	fun `검색 결과 Page를 PageResponse로 변환한다`() {
		val pageable = PageRequest.of(0, 2)
		val content = listOf(BookSummary(1L, "A", BookType.COMPUTER, 1_000, "김"))
		every { bookQueryRepository.search(any(), pageable) } returns PageImpl(content, pageable, 5)

		val result = bookService.search(BookSearchCondition(title = "A"), pageable)

		assertThat(result.totalElements).isEqualTo(5)
		assertThat(result.totalPages).isEqualTo(3)
		assertThat(result.content).isEqualTo(content)
	}
}
