package com.example.library.author

import com.example.library.book.BookRepository
import com.example.library.book.BookType
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 1:N / N:1 연관관계와 Projection 동작을 확인하는 테스트.
 */
@SpringBootTest
class AuthorRelationTest @Autowired constructor(
	private val authorService: AuthorService,
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
	private val entityManager: EntityManager,
	private val tx: TransactionTemplate,
) {

	@AfterEach
	fun clean() {
		bookRepository.deleteAllInBatch()
		authorRepository.deleteAllInBatch()
	}

	private fun saveAuthorWithBooks(name: String, vararg titles: String): Author =
		tx.execute {
			Author(name).apply { titles.forEach { writeBook(it, BookType.COMPUTER, 10_000) } }
				.also { authorRepository.save(it) } // cascade = ALL → Book도 함께 INSERT
		}!!

	@Test
	@DisplayName("cascade: Author만 save 해도 books가 함께 저장된다")
	fun cascadePersist() {
		val author = saveAuthorWithBooks("A", "책1", "책2")

		assertThat(bookRepository.findAllByAuthorIdOrderByIdAsc(author.id!!)).extracting("title")
			.containsExactly("책1", "책2")
	}

	@Test
	@DisplayName("@ManyToOne(LAZY): Book을 조회하면 author는 초기화되지 않은 프록시다 (allOpen 필요)")
	fun manyToOneLazyProxy() {
		val author = saveAuthorWithBooks("A", "책1")
		val bookId = bookRepository.findAllByAuthorIdOrderByIdAsc(author.id!!).single().id

		tx.executeWithoutResult {
			entityManager.clear()
			val book = bookRepository.findById(bookId).orElseThrow()
			assertThat(Hibernate.isInitialized(book.author)).isFalse() // 아직 SELECT 안 함
			assertThat(book.author.name).isEqualTo("A") // 접근하는 순간 SELECT
			assertThat(Hibernate.isInitialized(book.author)).isTrue()
		}
	}

	@Test
	@DisplayName("1:N fetch join: 작가 + 책 목록을 한 번에 가져와 DTO로 변환한다")
	fun fetchJoinCollection() {
		val author = saveAuthorWithBooks("A", "책1", "책2", "책3")

		val detail = authorService.getWithBooks(author.id!!)

		assertThat(detail.name).isEqualTo("A")
		assertThat(detail.books).hasSize(3) // Hibernate 6+ 는 fetch join 중복 행을 자동 제거 (DISTINCT 불필요)
	}

	@Test
	@DisplayName("JPQL 생성자 Projection: 책이 없는 작가도 LEFT JOIN으로 0권 집계된다")
	fun countBooksPerAuthor() {
		saveAuthorWithBooks("A", "책1", "책2")
		saveAuthorWithBooks("B")

		val result = authorService.countBooksPerAuthor()

		assertThat(result).extracting("authorName", "bookCount")
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("A", 2L),
				org.assertj.core.groups.Tuple.tuple("B", 0L),
			)
	}

	@Test
	@DisplayName("인터페이스 Projection: 필요한 컬럼만 조회한다")
	fun interfaceProjection() {
		val author = saveAuthorWithBooks("A", "책1", "책2")

		val titles = authorService.getBookTitles(author.id!!)

		assertThat(titles.map { it.title }).containsExactly("책1", "책2")
	}

	@Test
	@DisplayName("orphanRemoval: 작가를 삭제하면 그 작가의 책도 삭제된다")
	fun deleteCascade() {
		val author = saveAuthorWithBooks("A", "책1", "책2")

		authorService.delete(author.id!!)

		assertThat(bookRepository.count()).isZero()
	}
}
