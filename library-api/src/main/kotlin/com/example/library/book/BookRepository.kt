package com.example.library.book

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BookRepository : JpaRepository<Book, Long> {

	/** N:1 fetch join: Book과 Author를 한 번의 SELECT로 가져온다. Optional 대신 nullable 반환. */
	@Query("select b from Book b join fetch b.author where b.id = :id")
	fun findWithAuthorById(id: Long): Book?

	/** 인터페이스 기반 Projection: 선언한 프로퍼티(id, title)만 SELECT 한다. */
	fun findAllByAuthorIdOrderByIdAsc(authorId: Long): List<BookTitleView>
}

interface BookTitleView {
	val id: Long
	val title: String
}
