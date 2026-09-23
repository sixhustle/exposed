package com.example.library.book

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/**
 * API 레벨 CRUD 테스트. MockMvc Kotlin DSL(post { }, andExpect { })을 사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val bookRepository: BookRepository,
	private val authorRepository: AuthorRepository,
) {

	@AfterEach
	fun clean() {
		bookRepository.deleteAllInBatch()
		authorRepository.deleteAllInBatch()
	}

	@Test
	@DisplayName("CRUD 전체 흐름: 생성 201 → 조회 200 → 수정 200 → 삭제 204 → 조회 404")
	fun crudFlow() {
		val authorId = authorRepository.save(Author("김코틀")).id!!

		mockMvc.post("/api/books") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"코틀린 입문","type":"COMPUTER","price":20000,"authorId":$authorId}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.title") { value("코틀린 입문") }
			jsonPath("$.authorName") { value("김코틀") }
		}

		val bookId = bookRepository.findAll().single().id!!

		mockMvc.get("/api/books/$bookId").andExpect {
			status { isOk() }
			jsonPath("$.price") { value(20000) }
		}

		mockMvc.patch("/api/books/$bookId") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"코틀린 실전","type":"COMPUTER","price":25000}"""
		}.andExpect {
			status { isOk() }
			jsonPath("$.title") { value("코틀린 실전") }
		}

		mockMvc.get("/api/books?title=실전&page=0&size=10").andExpect {
			status { isOk() }
			jsonPath("$.totalElements") { value(1) }
			jsonPath("$.content[0].authorName") { value("김코틀") }
		}

		mockMvc.delete("/api/books/$bookId").andExpect { status { isNoContent() } }
		mockMvc.get("/api/books/$bookId").andExpect { status { isNotFound() } }
	}

	@Test
	@DisplayName("검증 실패: 빈 제목과 음수 가격은 400과 필드별 에러를 돌려준다")
	fun validationError() {
		val authorId = authorRepository.save(Author("A")).id!!

		mockMvc.post("/api/books") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"","type":"COMPUTER","price":-1,"authorId":$authorId}"""
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.errors.title") { exists() }
			jsonPath("$.errors.price") { exists() }
		}
	}

	@Test
	@DisplayName("non-null 필드 누락: Kotlin 모듈이 역직렬화 단계에서 막아 400")
	fun missingRequiredField() {
		mockMvc.post("/api/books") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"제목만"}"""
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	@DisplayName("잘못된 Enum 값은 400")
	fun invalidEnum() {
		val authorId = authorRepository.save(Author("A")).id!!

		mockMvc.post("/api/books") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"책","type":"COMPUTERR","price":1,"authorId":$authorId}"""
		}.andExpect { status { isBadRequest() } }
	}
}
