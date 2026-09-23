package com.example.library.author

import com.example.library.book.BookRepository
import com.example.library.book.BookType
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

/**
 * @DataJpaTest 를 제대로 쓰는 법.
 *
 * 무엇이 뜨나
 * - JPA 관련 Bean만: DataSource, EntityManager, @Entity, Repository, TestEntityManager
 * - @Service · @Component · Controller 는 뜨지 않는다 (필요하면 @Import)
 *
 * 기본 동작
 * - 각 테스트가 @Transactional 로 감싸지고 끝나면 롤백 → 정리 코드가 필요 없다
 * - 임베디드 DB(H2)로 자동 교체된다. 진짜 DB로 검증하려면
 *   @AutoConfigureTestDatabase(replace = NONE) + Testcontainers (MySqlContainerTest 참고)
 *
 * TestEntityManager
 * - persist/flush/clear 를 직접 제어해 "영속성 컨텍스트 때문에 통과하는 가짜 성공"을 막는다
 */
@DataJpaTest
class AuthorDataJpaTest @Autowired constructor(
	private val em: TestEntityManager,
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
) {

	@Test
	@DisplayName("flush + clear 를 해야 진짜 DB에서 다시 읽어 온다")
	fun flushAndClear() {
		val author = Author("김코틀").apply { writeBook("코틀린", BookType.COMPUTER, 10_000) }
		em.persist(author)

		em.flush() // 여기서 INSERT SQL이 나간다 (flush 전에는 쿼리가 나가지 않는다)
		em.clear() // 1차 캐시를 비운다 → 아래 조회는 진짜 SELECT

		val found = authorRepository.findById(author.id!!).orElseThrow()
		assertThat(found).isNotSameAs(author) // 캐시가 비었으므로 새 인스턴스
		assertThat(Hibernate.isInitialized(found.books)).isFalse() // 컬렉션은 아직 LAZY 프록시
		assertThat(found.books).hasSize(1) // 접근하는 순간 초기화
	}

	@Test
	@DisplayName("clear 하지 않으면 1차 캐시가 돌려줘서 SQL 없이 통과한다 (가짜 성공 주의)")
	fun firstLevelCache() {
		val author = em.persist(Author("김코틀"))
		em.flush()

		val found = authorRepository.findById(author.id!!).orElseThrow()

		assertThat(found).isSameAs(author) // 같은 인스턴스 = SELECT가 나가지 않았다
	}

	@Test
	@DisplayName("메소드 이름 쿼리도 슬라이스 테스트로 검증한다")
	fun derivedQuery() {
		em.persist(Author("김코틀"))
		em.flush()
		em.clear()

		assertThat(authorRepository.existsByName("김코틀")).isTrue()
		assertThat(authorRepository.existsByName("없는사람")).isFalse()
	}

	@Test
	@DisplayName("테스트가 끝나면 롤백되므로 데이터가 남지 않는다")
	fun rollback() {
		em.persist(Author("사라질 작가"))
		em.flush()

		assertThat(authorRepository.count()).isEqualTo(1) // 이 테스트 안에서만 보인다
		assertThat(bookRepository.count()).isZero()
		// 종료 시 롤백 → 다른 테스트에 영향을 주지 않는다 (@AfterEach 정리 불필요)
	}
}
