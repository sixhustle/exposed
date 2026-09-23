package com.example.library.book.dto

import com.example.library.book.Book
import com.example.library.book.BookType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// ── 요청 DTO ───────────────────────────────────────────────
// @field: → 어노테이션을 (생성자 파라미터가 아니라) 필드에 붙여야 Bean Validation이 읽는다.

data class BookCreateRequest(
	@field:NotBlank @field:Size(max = 200)
	val title: String,
	val type: BookType,
	@field:Min(0)
	val price: Int,
	val authorId: Long,
)

data class BookUpdateRequest(
	@field:NotBlank @field:Size(max = 200)
	val title: String,
	val type: BookType,
	@field:Min(0)
	val price: Int,
	val authorId: Long? = null, // null이면 작가는 그대로
)

/** 검색 조건: 모든 값이 선택(nullable) → Querydsl 동적 쿼리에서 null 조건은 무시된다. */
data class BookSearchCondition(
	val title: String? = null,
	val type: BookType? = null,
	val authorName: String? = null,
	val minPrice: Int? = null,
	val maxPrice: Int? = null,
)

// ── 응답 DTO ───────────────────────────────────────────────

data class BookResponse(
	val id: Long,
	val title: String,
	val type: BookType,
	val price: Int,
	val authorId: Long,
	val authorName: String,
	val createdAt: LocalDateTime,
) {
	companion object {
		// 정적 팩토리: Entity → DTO 변환 책임을 DTO에 둔다. book.author 접근은 트랜잭션 안에서만.
		fun of(book: Book) = BookResponse(
			id = book.id!!, // 저장된 엔티티이므로 id가 있음을 단언
			title = book.title,
			type = book.type,
			price = book.price,
			authorId = book.author.id!!,
			authorName = book.author.name,
			createdAt = book.createdAt,
		)
	}
}

/** Querydsl Projections.constructor로 바로 채우는 목록용 DTO (엔티티를 거치지 않는다). */
data class BookSummary(
	val id: Long,
	val title: String,
	val type: BookType,
	val price: Int,
	val authorName: String,
)

data class BookTypeCount(
	val type: BookType,
	val count: Long,
)
