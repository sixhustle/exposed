package com.example.library.book

import com.example.library.book.dto.BookResponse
import com.example.library.common.exception.NotFoundException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

/**
 * @WebMvcTest: Controller · ControllerAdvice · JSON 변환만 띄우는 슬라이스 테스트. DB를 쓰지 않는다.
 * Service는 @MockitoBean 가짜 객체로 바꾸고 "요청 → 응답 형식/상태 코드"만 검증한다.
 * (BookService는 plugin.spring 덕분에 open 클래스라 Mockito가 프록시를 만들 수 있다)
 */
@WebMvcTest(BookController::class)
class BookControllerWebMvcTest @Autowired constructor(
	private val mockMvc: MockMvc,
) {

	@MockitoBean
	lateinit var bookService: BookService // 필드 주입이 필요하므로 lateinit var

	@Test
	fun `조회 성공 시 200과 JSON 본문`() {
		given(bookService.get(1L)).willReturn(
			BookResponse(1L, "코틀린", BookType.COMPUTER, 20_000, 10L, "김코틀", LocalDateTime.of(2026, 9, 22, 0, 0)),
		)

		mockMvc.get("/api/books/1").andExpect {
			status { isOk() }
			jsonPath("$.title") { value("코틀린") }
			jsonPath("$.authorName") { value("김코틀") }
		}
	}

	@Test
	fun `서비스가 NotFoundException을 던지면 404 ProblemDetail`() {
		given(bookService.get(99L)).willThrow(NotFoundException("Book(id=99)를 찾을 수 없습니다"))

		mockMvc.get("/api/books/99").andExpect {
			status { isNotFound() }
			jsonPath("$.detail") { value("Book(id=99)를 찾을 수 없습니다") }
		}
	}
}
