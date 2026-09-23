package com.example.library.book

import com.example.library.author.Author
import com.example.library.author.AuthorRepository
import com.example.library.book.dto.BookSearchCondition
import com.example.library.common.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest

/**
 * @DataJpaTest: JPA 관련 Bean(EntityManager, Repository, DataSource)만 띄우는 슬라이스 테스트.
 * - Controller/Service는 로딩하지 않아 @SpringBootTest보다 가볍다.
 * - 각 테스트가 @Transactional로 감싸져 끝나면 롤백 → @AfterEach 정리가 필요 없다.
 * - @Repository 클래스(BookQueryRepository)와 JPAQueryFactory 설정은 자동 등록되지 않으므로 @Import 한다.
 */
@DataJpaTest
@Import(JpaConfig::class, BookQueryRepository::class)
class BookQueryRepositoryTest @Autowired constructor(
	private val authorRepository: AuthorRepository,
	private val bookQueryRepository: BookQueryRepository,
) {

	@Test
	fun `조건이 모두 null이면 전체를 최신순으로 조회한다`() {
		authorRepository.save(
			Author("A").apply {
				writeBook("첫 번째", BookType.COMPUTER, 1_000)
				writeBook("두 번째", BookType.SCIENCE, 2_000)
			},
		)

		val page = bookQueryRepository.search(BookSearchCondition(), PageRequest.of(0, 10))

		assertThat(page.content).extracting("title").containsExactly("두 번째", "첫 번째")
	}

	@Test
	fun `분야와 가격 범위 조건을 AND로 적용한다`() {
		authorRepository.save(
			Author("A").apply {
				writeBook("싼 컴퓨터 책", BookType.COMPUTER, 5_000)
				writeBook("비싼 컴퓨터 책", BookType.COMPUTER, 50_000)
				writeBook("싼 과학 책", BookType.SCIENCE, 5_000)
			},
		)

		val page = bookQueryRepository.search(
			BookSearchCondition(type = BookType.COMPUTER, maxPrice = 10_000),
			PageRequest.of(0, 10),
		)

		assertThat(page.content).extracting("title").containsExactly("싼 컴퓨터 책")
	}
}
