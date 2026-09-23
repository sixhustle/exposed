package com.example.library.author

import com.example.library.book.BookType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AuthorCreateRequest(
	@field:NotBlank @field:Size(max = 50)
	val name: String,
)

data class AuthorResponse(
	val id: Long,
	val name: String,
) {
	companion object {
		fun of(author: Author) = AuthorResponse(id = author.id!!, name = author.name)
	}
}

/** 1:N 응답: 엔티티(Author, Book)를 그대로 반환하지 않고 DTO로 변환한다 → 순환 참조/지연 로딩 예외 방지 */
data class AuthorDetailResponse(
	val id: Long,
	val name: String,
	val books: List<BookItem>,
) {
	data class BookItem(
		val id: Long,
		val title: String,
		val type: BookType,
		val price: Int,
	)

	companion object {
		fun of(author: Author) = AuthorDetailResponse(
			id = author.id!!,
			name = author.name,
			books = author.books.map { BookItem(it.id!!, it.title, it.type, it.price) },
		)
	}
}
