package com.example.library.book

import com.example.library.author.AuthorRepository
import com.example.library.book.dto.BookCreateRequest
import com.example.library.book.dto.BookResponse
import com.example.library.book.dto.BookSearchCondition
import com.example.library.book.dto.BookSummary
import com.example.library.book.dto.BookTypeCount
import com.example.library.book.dto.BookUpdateRequest
import com.example.library.common.PageResponse
import com.example.library.common.exception.NotFoundException
import com.example.library.common.findByIdOrThrow
import com.example.library.event.BookEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true) // 기본은 읽기 전용, 쓰기 메소드에만 @Transactional
class BookService(
	private val bookRepository: BookRepository,
	private val bookQueryRepository: BookQueryRepository,
	private val authorRepository: AuthorRepository,
	private val eventPublisher: ApplicationEventPublisher,
) {

	@Transactional
	@CacheEvict(cacheNames = ["bookStats"], allEntries = true) // 통계가 바뀌므로 캐시를 비운다
	fun create(request: BookCreateRequest): BookResponse {
		val author = authorRepository.findByIdOrThrow(request.authorId)
		val book = author.writeBook(request.title, request.type, request.price)
		val saved = bookRepository.save(book) // IDENTITY 전략: save 시점에 INSERT가 실행되어 id가 채워진다
		// 여기서 바로 Kafka로 보내지 않는다. 스프링 이벤트만 쏘고, 커밋된 뒤에 BookEventPublisher가 발행한다
		eventPublisher.publishEvent(BookEvent(BookEvent.Type.CREATED, saved.id!!, saved.title, saved.type))
		return BookResponse.of(saved)
	}

	fun get(id: Long): BookResponse {
		val book = bookRepository.findWithAuthorById(id) ?: throw NotFoundException("Book(id=$id)를 찾을 수 없습니다")
		return BookResponse.of(book)
	}

	fun search(condition: BookSearchCondition, pageable: Pageable): PageResponse<BookSummary> =
		PageResponse.of(bookQueryRepository.search(condition, pageable))

	// 자주 조회되고 자주 바뀌지 않는 통계 → Redis 캐시. 키는 "bookStats::all"
	@Cacheable(cacheNames = ["bookStats"], key = "'all'")
	fun countByType(): List<BookTypeCount> = bookQueryRepository.countByType()

	@Transactional
	fun update(id: Long, request: BookUpdateRequest): BookResponse {
		val book = bookRepository.findWithAuthorById(id) ?: throw NotFoundException("Book(id=$id)를 찾을 수 없습니다")
		book.update(request.title, request.type, request.price)
		request.authorId?.let { book.changeAuthor(authorRepository.findByIdOrThrow(it)) }
		// save() 호출 없음: 영속 상태 엔티티는 트랜잭션 커밋 시 변경 감지(dirty checking)로 UPDATE 된다
		return BookResponse.of(book)
	}

	@Transactional
	@CacheEvict(cacheNames = ["bookStats"], allEntries = true)
	fun delete(id: Long) {
		val book = bookRepository.findWithAuthorById(id) ?: throw NotFoundException("Book(id=$id)를 찾을 수 없습니다")
		book.author.removeBook(book) // 메모리의 양방향 컬렉션도 맞춰 둔다
		bookRepository.delete(book)
		eventPublisher.publishEvent(BookEvent(BookEvent.Type.DELETED, id, book.title, book.type))
	}
}
