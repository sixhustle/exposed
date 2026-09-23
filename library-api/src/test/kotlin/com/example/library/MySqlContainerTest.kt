package com.example.library

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.BookQueryRepository
import com.example.library.book.BookRepository
import com.example.library.book.BookType
import com.example.library.book.dto.BookSearchCondition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

/**
 * 실제 MySQL(Docker)로 스키마 생성 · Querydsl 검색을 검증한다.
 * Docker가 꺼져 있으면 disabledWithoutDocker 로 자동 건너뛴다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlContainerTest @Autowired constructor(
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
	private val bookQueryRepository: BookQueryRepository,
) {

	companion object {
		// @Container + @ServiceConnection: 컨테이너를 띄우고 datasource 접속 정보를 자동 주입
		// static 필드여야 클래스당 한 번만 뜨므로 companion object + @JvmStatic
		@Container
		@ServiceConnection
		@JvmStatic
		val mysql = MySQLContainer("mysql:8.4")
	}

	@Test
	fun `MySQL에서 저장과 동적 검색이 동작한다`() {
		val author = Author("김코틀").apply { writeBook("코틀린 입문", BookType.COMPUTER, 20_000) }
		authorRepository.save(author)

		val page = bookQueryRepository.search(BookSearchCondition(title = "코틀린"), PageRequest.of(0, 10))

		assertThat(page.content).extracting("authorName").containsExactly("김코틀")
		bookRepository.deleteAllInBatch()
		authorRepository.deleteAllInBatch()
	}
}
