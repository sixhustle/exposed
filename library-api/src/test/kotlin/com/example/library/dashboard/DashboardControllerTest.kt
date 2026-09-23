package com.example.library.dashboard

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.BookRepository
import com.example.library.book.BookType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * suspend 컨트롤러 테스트. 코루틴 응답은 "비동기"로 처리되므로
 * 1) 요청이 비동기로 시작됐는지 확인하고 2) asyncDispatch 로 결과를 받아 검증한다.
 * (BookControllerTest와 설정이 같아서 스프링 컨텍스트를 재사용한다)
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
) {

	@AfterEach
	fun clean() {
		bookRepository.deleteAllInBatch()
		authorRepository.deleteAllInBatch()
	}

	@Test
	fun `대시보드는 분야별 권수와 작가별 권수를 함께 돌려준다`() {
		authorRepository.save(
			Author("김코틀").apply {
				writeBook("코틀린 입문", BookType.COMPUTER, 20_000)
				writeBook("코루틴", BookType.COMPUTER, 25_000)
			},
		)

		val started = mockMvc.get("/api/dashboard").andExpect { request { asyncStarted() } }.andReturn()

		mockMvc.perform(asyncDispatch(started))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.typeCounts[0].type").value("COMPUTER"))
			.andExpect(jsonPath("$.typeCounts[0].count").value(2))
			.andExpect(jsonPath("$.authorCounts[0].authorName").value("김코틀"))
	}
}
