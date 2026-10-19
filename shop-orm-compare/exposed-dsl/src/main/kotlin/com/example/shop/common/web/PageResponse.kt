package com.example.shop.common.web

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
	val content: List<T>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
) {
	companion object {
		fun <T : Any> of(page: Page<T>) = PageResponse(page.content, page.number, page.size, page.totalElements, page.totalPages)
	}
}

/** 커서(keyset) 페이지. size + 1개를 조회해 다음 페이지 여부를 판단한다. */
data class CursorResponse<T : Any>(
	val content: List<T>,
	val nextCursor: Long?,
	val hasNext: Boolean,
) {
	companion object {
		fun <T : Any> of(fetched: List<T>, size: Int, cursorOf: (T) -> Long): CursorResponse<T> {
			val hasNext = fetched.size > size
			val content = fetched.take(size)
			return CursorResponse(content, if (hasNext) cursorOf(content.last()) else null, hasNext)
		}
	}
}
