package com.example.library.author

import com.example.library.book.BookQueryRepository
import com.example.library.book.BookRepository
import com.example.library.book.BookTitleView
import com.example.library.common.exception.BusinessException
import com.example.library.common.exception.NotFoundException
import com.example.library.common.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthorService(
	private val authorRepository: AuthorRepository,
	private val bookRepository: BookRepository,
	private val bookQueryRepository: BookQueryRepository,
) {

	@Transactional
	fun create(request: AuthorCreateRequest): AuthorResponse {
		if (authorRepository.existsByName(request.name)) {
			throw BusinessException("이미 등록된 작가입니다: ${request.name}")
		}
		return AuthorResponse.of(authorRepository.save(Author(request.name)))
	}

	/** 1:N fetch join 한 번으로 작가 + 책 목록 */
	fun getWithBooks(id: Long): AuthorDetailResponse {
		val author = bookQueryRepository.findAuthorWithBooks(id) ?: throw NotFoundException("Author(id=$id)를 찾을 수 없습니다")
		return AuthorDetailResponse.of(author)
	}

	/** 인터페이스 Projection: 제목만 필요할 때 엔티티 전체를 읽지 않는다 */
	fun getBookTitles(id: Long): List<BookTitleView> {
		if (!authorRepository.existsById(id)) throw NotFoundException("Author(id=$id)를 찾을 수 없습니다")
		return bookRepository.findAllByAuthorIdOrderByIdAsc(id)
	}

	fun countBooksPerAuthor(): List<AuthorBookCount> = authorRepository.countBooksPerAuthor()

	@Transactional
	fun rename(id: Long, name: String): AuthorResponse {
		val author = authorRepository.findByIdOrThrow(id)
		author.rename(name)
		return AuthorResponse.of(author)
	}

	/** cascade = ALL + orphanRemoval → 작가를 지우면 그 작가의 책도 함께 DELETE */
	@Transactional
	fun delete(id: Long) {
		authorRepository.delete(authorRepository.findByIdOrThrow(id))
	}
}
