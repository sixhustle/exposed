package com.example.library.book

import com.example.library.author.Author
import com.example.library.author.QAuthor.author
import com.example.library.book.QBook.book
import com.example.library.book.dto.BookSearchCondition
import com.example.library.book.dto.BookSummary
import com.example.library.book.dto.BookTypeCount
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository

/**
 * Querydsl 전용 Repository (별도 클래스 방식).
 * Service는 단순 CRUD는 BookRepository, 복잡한 조회는 이 클래스를 사용한다.
 */
@Repository
class BookQueryRepository(
	private val queryFactory: JPAQueryFactory,
) {

	/** 동적 검색 + 페이징 + DTO Projection */
	fun search(condition: BookSearchCondition, pageable: Pageable): Page<BookSummary> {
		val content = queryFactory
			.select(
				Projections.constructor(
					BookSummary::class.java,
					book.id,
					book.title,
					book.type,
					book.price,
					author.name,
				),
			)
			.from(book)
			.join(book.author, author) // N:1 join은 row 수가 늘지 않아 페이징에 안전하다
			.where(*conditions(condition))
			.orderBy(book.id.desc())
			.offset(pageable.offset)
			.limit(pageable.pageSize.toLong())
			.fetch()

		// count 쿼리는 필요할 때만 실행된다 (마지막 페이지이거나 첫 페이지 결과가 size보다 작으면 생략)
		return PageableExecutionUtils.getPage(content, pageable) {
			queryFactory
				.select(book.count())
				.from(book)
				.join(book.author, author)
				.where(*conditions(condition))
				.fetchOne() ?: 0L
		}
	}

	/** 1:N 컬렉션 fetch join: Author와 books를 한 번에. 단건 조회라 페이징 문제가 없다. */
	fun findAuthorWithBooks(authorId: Long): Author? =
		queryFactory
			.selectFrom(author)
			.leftJoin(author.books, book).fetchJoin() // 연관관계 경로(author.books)로 조인해야 fetch join이 적용된다
			.where(author.id.eq(authorId))
			.fetchOne()

	/** GROUP BY 집계 → DTO */
	fun countByType(): List<BookTypeCount> =
		queryFactory
			.select(Projections.constructor(BookTypeCount::class.java, book.type, book.id.count()))
			.from(book)
			.groupBy(book.type)
			.orderBy(book.type.asc())
			.fetch()

	private fun conditions(condition: BookSearchCondition): Array<BooleanExpression?> = arrayOf(
		condition.title?.takeIf { it.isNotBlank() }?.let { book.title.contains(it) },
		condition.type?.let { book.type.eq(it) },
		condition.authorName?.takeIf { it.isNotBlank() }?.let { author.name.eq(it) },
		condition.minPrice?.let { book.price.goe(it) },
		condition.maxPrice?.let { book.price.loe(it) },
	) // where()는 null 조건을 무시하고 나머지를 AND로 묶는다
}
