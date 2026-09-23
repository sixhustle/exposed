package com.example.library.book

import com.example.library.author.Author
import com.example.library.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * N:1 의 "N" 쪽. author_id FK를 가진 연관관계의 주인이다.
 */
@Entity
@Table(name = "book", indexes = [Index(name = "idx_book_title", columnList = "title")])
class Book(
	title: String,
	type: BookType,
	price: Int,
	author: Author,
	id: Long? = null, // 보통은 생략(DB가 채움). 테스트에서 id가 있는 객체를 만들 때만 지정
) : BaseTimeEntity() {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = id

	@Column(nullable = false, length = 200) // Kotlin의 String(non-null)만으로는 NOT NULL DDL이 생기지 않는다
	var title: String = title
		protected set

	@Enumerated(EnumType.STRING) // 기본값 ORDINAL(순서 숫자)은 쓰지 않는다
	@Column(nullable = false, length = 20)
	var type: BookType = type
		protected set

	@Column(nullable = false)
	var price: Int = price
		protected set

	// @ManyToOne 기본값은 EAGER → 반드시 LAZY로. LAZY 프록시는 allOpen(Entity open)이 있어야 동작한다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	var author: Author = author
		protected set

	init {
		validate(title, price)
	}

	fun update(title: String, type: BookType, price: Int) {
		validate(title, price)
		this.title = title
		this.type = type
		this.price = price
	}

	/** 주인(Book.author)을 바꾸고, 양쪽 Author의 컬렉션도 함께 맞춘다. */
	fun changeAuthor(newAuthor: Author) {
		if (author === newAuthor) return
		author.removeBook(this)
		author = newAuthor
		newAuthor.addBook(this)
	}

	private fun validate(title: String, price: Int) {
		require(title.isNotBlank()) { "책 제목은 비어 있을 수 없습니다" }
		require(price >= 0) { "가격은 0 이상이어야 합니다" }
	}
}
