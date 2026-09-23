package com.example.library.common

import org.springframework.data.domain.Page

/**
 * Page(PageImpl)를 그대로 JSON으로 내보내면 내부 구조가 API 스펙이 되어 버린다.
 * 필요한 필드만 가진 응답 DTO로 감싼다.
 */
data class PageResponse<T>(
	val content: List<T>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
) {
	companion object {
		fun <T : Any> of(page: Page<T>) = PageResponse(
			content = page.content,
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages,
		)
	}
}
