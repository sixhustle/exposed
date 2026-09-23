package com.example.library.dashboard

import com.example.library.author.AuthorBookCount
import com.example.library.author.AuthorService
import com.example.library.book.BookService
import com.example.library.book.BookType
import com.example.library.book.dto.BookTypeCount
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Mock + 코루틴 테스트: 두 조회가 정말 "동시에" 실행되는지 실제 시간으로 확인한다.
 * (여기서는 Thread.sleep 으로 느린 DB 호출을 흉내 내므로 가상 시간이 아니라 실제 시간을 잰다)
 */
class DashboardServiceTest {

	private val bookService = mockk<BookService>()
	private val authorService = mockk<AuthorService>()
	private val dashboardService = DashboardService(bookService, authorService)

	@Test
	fun `두 통계를 동시에 조회해서 한 번에 돌려준다`() = runTest {
		every { bookService.countByType() } answers {
			Thread.sleep(300) // 300ms 걸리는 쿼리라고 가정
			listOf(BookTypeCount(BookType.COMPUTER, 2))
		}
		every { authorService.countBooksPerAuthor() } answers {
			Thread.sleep(300)
			listOf(AuthorBookCount(1L, "김코틀", 2))
		}

		val start = System.nanoTime()
		val result = dashboardService.getDashboard()
		val elapsedMs = (System.nanoTime() - start) / 1_000_000

		assertThat(result.typeCounts).hasSize(1)
		assertThat(result.authorCounts).hasSize(1)
		assertThat(elapsedMs).isLessThan(550) // 순서대로였다면 600ms 이상. 동시에 실행돼서 약 300ms
		println("dashboard elapsed = ${elapsedMs}ms")
	}
}
