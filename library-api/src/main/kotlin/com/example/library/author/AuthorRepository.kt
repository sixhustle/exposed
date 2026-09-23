package com.example.library.author

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AuthorRepository : JpaRepository<Author, Long> {

	fun existsByName(name: String): Boolean

	/** JPQL 생성자 표현식(DTO Projection): 엔티티 대신 필요한 컬럼만 DTO로 받는다. */
	@Query(
		"""
		select new com.example.library.author.AuthorBookCount(a.id, a.name, count(b))
		from Author a left join a.books b
		group by a.id, a.name
		order by a.id
		""",
	)
	fun countBooksPerAuthor(): List<AuthorBookCount>
}

data class AuthorBookCount(
	val authorId: Long,
	val authorName: String,
	val bookCount: Long, // JPQL count()의 결과 타입은 Long
)
