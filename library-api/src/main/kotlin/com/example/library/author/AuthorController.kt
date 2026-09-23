package com.example.library.author

import com.example.library.book.BookTitleView
import jakarta.validation.Valid
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
@RequestMapping("/api/authors")
class AuthorController(
	private val authorService: AuthorService,
) {

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@Valid @RequestBody request: AuthorCreateRequest): AuthorResponse = authorService.create(request)

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): AuthorDetailResponse = authorService.getWithBooks(id)

	@GetMapping("/{id}/book-titles")
	fun getBookTitles(@PathVariable id: Long): List<BookTitleView> = authorService.getBookTitles(id)

	@GetMapping("/stats/book-counts")
	fun countBooksPerAuthor(): List<AuthorBookCount> = authorService.countBooksPerAuthor()

	@PatchMapping("/{id}")
	fun rename(@PathVariable id: Long, @Valid @RequestBody request: AuthorCreateRequest): AuthorResponse =
		authorService.rename(id, request.name)

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: Long) = authorService.delete(id)
}
