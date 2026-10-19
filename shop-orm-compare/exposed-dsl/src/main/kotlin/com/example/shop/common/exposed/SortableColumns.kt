package com.example.shop.common.exposed

import com.example.shop.common.error.InvalidSortPropertyException
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.SortOrder
import org.springframework.data.domain.Sort

/**
 * 정렬 화이트리스트: 클라이언트의 sort 파라미터를 허용한 컬럼으로만 바꾼다. 없는 키는 SQL을 만들기 전에 400.
 * 정렬이 없으면 id 내림차순.
 */
class SortableColumns(private val columns: Map<String, Expression<*>>, private val id: Expression<*>) {
	fun toOrderBy(sort: Sort): Array<Pair<Expression<*>, SortOrder>> {
		if (sort.isUnsorted) return arrayOf(id to SortOrder.DESC)
		return sort.map { order ->
			val column = columns[order.property] ?: throw InvalidSortPropertyException(order.property, columns.keys)
			column to if (order.isAscending) SortOrder.ASC else SortOrder.DESC
		}.toList().toTypedArray()
	}
}

/** prefix 검색 패턴. 입력의 %·_ 는 이스케이프된다 (LIKE '50\%%' ESCAPE '\') */
fun startsWith(keyword: String): LikePattern = LikePattern.ofLiteral(keyword) + "%"
