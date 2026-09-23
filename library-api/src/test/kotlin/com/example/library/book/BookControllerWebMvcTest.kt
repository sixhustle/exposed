package com.example.library.book

import com.example.library.book.dto.BookResponse
import com.example.library.common.exception.NotFoundException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * @WebMvcTest: Controller · ControllerAdvice · JSON 변환만 띄우는 슬라이스 테스트. DB를 쓰지 않는다.
 * Service는 @MockitoBean 가짜 객체로 바꾸고 "요청 → 응답 형식/상태 코드"만 검증한다.
 * (BookService는 plugin.spring 덕분에 open 클래스라 Mockito가 프록시를 만들 수 있다)
 *
 * 무엇이 뜨나
 * - @Controller/@RestController, @RestControllerAdvice, 메시지 컨버터(Jackson), 필터, MockMvc
 * - @Service · @Repository · JPA 는 뜨지 않는다 → 필요한 협력 객체는 @MockitoBean 으로
 * - 괄호 안에 컨트롤러를 지정(WebMvcTest(BookController::class))하지 않으면 모든 컨트롤러가 뜬다
 *
 * 주의
 * - @EnableJpaAuditing 을 메인 클래스에 붙이면 JPA 없이 뜨는 이 테스트가 깨진다 → 별도 Config로
 * - 형제 슬라이스: @JsonTest(직렬화만), @RestClientTest(외부 API 호출), @DataRedisTest 등
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
	fun `검증 실패는 DB 없이도 400과 필드별 에러를 돌려준다`() {
		// @Valid → MethodArgumentNotValidException → GlobalExceptionHandler
		// 서비스는 호출조차 되지 않으므로 stub이 필요 없다
		mockMvc.post("/api/books") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"","type":"COMPUTER","price":-1,"authorId":1}"""
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.errors.title") { exists() }
			jsonPath("$.errors.price") { exists() }
		}
	}

	@Test
	fun `Kotlin non-null 필드가 없으면 역직렬화 단계에서 400`() {
		mockMvc.post("/api/books") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"제목만"}"""
		}.andExpect { status { isBadRequest() } }
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
