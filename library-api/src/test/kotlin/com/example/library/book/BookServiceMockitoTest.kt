package com.example.library.book

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.dto.BookResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

/**
 * 같은 Mock 단위 테스트를 Mockito(스프링 기본 포함)로 작성한 버전. 스프링 컨텍스트는 띄우지 않는다.
 *
 * Kotlin + Mockito 주의점
 * - @Mock/@InjectMocks 필드는 나중에 채워지므로 lateinit var
 * - Mockito의 any()는 null을 반환해 Kotlin non-null 파라미터에서 NPE가 날 수 있다
 *   → 구체적인 값으로 stub 하거나, mockito-kotlin(any<T>()) 또는 MockK를 쓴다
 */
@ExtendWith(MockitoExtension::class)
class BookServiceMockitoTest {

	@Mock
	lateinit var bookRepository: BookRepository

	@Mock
	lateinit var bookQueryRepository: BookQueryRepository

	@Mock
	lateinit var authorRepository: AuthorRepository

	@Mock
	lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher

	@InjectMocks
	lateinit var bookService: BookService // 생성자에 위 Mock들을 넣어 만들어 준다

	@Test
	fun `id로 책을 조회해 응답 DTO로 변환한다`() {
		val book = Book("코틀린", BookType.COMPUTER, 20_000, Author("김코틀", id = 1L), id = 10L)
		given(bookRepository.findWithAuthorById(10L)).willReturn(book)

		val response: BookResponse = bookService.get(10L)

		assertThat(response.title).isEqualTo("코틀린")
		assertThat(response.authorName).isEqualTo("김코틀")
	}
}
