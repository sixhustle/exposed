package com.example.library.author

import com.example.library.book.Book
import com.example.library.book.BookType
import com.example.library.common.BaseTimeEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

/**
 * 1:N 의 "1" 쪽. 연관관계의 주인이 아니다(mappedBy) → 이 컬렉션을 바꿔도 FK는 바뀌지 않는다.
 */
@Entity
@Table(name = "author")
class Author(
	name: String,
	id: Long? = null, // 보통은 생략(DB가 채움). 테스트에서 id가 있는 객체를 만들 때만 지정
) : BaseTimeEntity() {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
	val id: Long? = id

	@Column(nullable = false, length = 50)
	var name: String = name
		protected set // allOpen으로 open된 프로퍼티는 private set 불가 → protected set

	// cascade ALL + orphanRemoval: Author를 저장/삭제하면 Book도 함께, 컬렉션에서 빠진 Book은 DELETE
	// 외부에서 books.add()를 직접 호출하지 말고 writeBook()/removeBook()을 사용한다.
	@OneToMany(mappedBy = "author", cascade = [CascadeType.ALL], orphanRemoval = true)
	val books: MutableList<Book> = mutableListOf()

	init {
		require(name.isNotBlank()) { "작가 이름은 비어 있을 수 없습니다" }
	}

	fun rename(name: String) {
		require(name.isNotBlank()) { "작가 이름은 비어 있을 수 없습니다" }
		this.name = name
	}

	/** 양방향 연관관계 편의 메소드: Book.author(주인)와 Author.books(거울)를 한 번에 맞춘다. */
	fun writeBook(title: String, type: BookType, price: Int): Book =
		Book(title = title, type = type, price = price, author = this).also { books.add(it) }

	internal fun addBook(book: Book) {
		if (book !in books) books.add(book)
	}

	internal fun removeBook(book: Book) {
		books.remove(book)
	}
}
