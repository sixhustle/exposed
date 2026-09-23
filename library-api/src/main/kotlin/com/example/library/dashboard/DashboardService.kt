package com.example.library.dashboard

import com.example.library.author.AuthorBookCount
import com.example.library.author.AuthorService
import com.example.library.book.BookService
import com.example.library.book.dto.BookTypeCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

data class DashboardResponse(
	val typeCounts: List<BookTypeCount>,
	val authorCounts: List<AuthorBookCount>,
)

/**
 * 코루틴 예제: 서로 관계없는 두 통계 조회를 "동시에" 실행한다.
 *
 * - 순서대로 부르면: A(300ms) → B(300ms) = 약 600ms
 * - async로 동시에 부르면: max(A, B) = 약 300ms
 *
 * 주의
 * - JPA/JDBC 호출은 스레드를 붙잡는(blocking) 작업이라 Dispatchers.IO 에서 실행한다.
 * - 이 클래스에는 @Transactional을 붙이지 않는다. 스프링 트랜잭션은 "스레드"에 묶여 있어서,
 *   다른 스레드로 넘어간 async 블록 안에서는 바깥 트랜잭션이 보이지 않는다.
 *   대신 호출되는 각 서비스 메소드가 자기 스레드에서 각자 readOnly 트랜잭션을 연다.
 */
@Service
class DashboardService(
	private val bookService: BookService,
	private val authorService: AuthorService,
) {

	suspend fun getDashboard(): DashboardResponse = coroutineScope {
		val typeCounts = async(Dispatchers.IO) { bookService.countByType() }
		val authorCounts = async(Dispatchers.IO) { authorService.countBooksPerAuthor() }
		// await(): 결과가 나올 때까지 "기다리되", 스레드를 붙잡지 않고 잠시 멈춘다(suspend)
		DashboardResponse(typeCounts.await(), authorCounts.await())
	}
}
