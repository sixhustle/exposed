package com.example.library.book

import com.example.library.book.dto.BookCreateRequest
import com.example.library.book.dto.BookResponse
import com.example.library.book.dto.BookSearchCondition
import com.example.library.book.dto.BookSummary
import com.example.library.book.dto.BookTypeCount
import com.example.library.book.dto.BookUpdateRequest
import com.example.library.common.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/books")
class BookController(
	private val bookService: BookService,
) {

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@Valid @RequestBody request: BookCreateRequest): BookResponse = bookService.create(request)

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): BookResponse = bookService.get(id)

	// GET /api/books?title=코틀린&type=COMPUTER&minPrice=10000&page=0&size=20
	// 쿼리 파라미터는 BookSearchCondition 생성자 인자로 바인딩된다 (@ModelAttribute 생략)
	@GetMapping
	fun search(
		condition: BookSearchCondition,
		@PageableDefault(size = 20) pageable: Pageable,
	): PageResponse<BookSummary> = bookService.search(condition, pageable)

	@GetMapping("/stats/types")
	fun countByType(): List<BookTypeCount> = bookService.countByType()

	@PatchMapping("/{id}")
	fun update(@PathVariable id: Long, @Valid @RequestBody request: BookUpdateRequest): BookResponse =
		bookService.update(id, request)

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: Long) = bookService.delete(id)
}
