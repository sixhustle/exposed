package com.example.shop.order

import com.example.shop.user.Users
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository

/** 조회 모델: orders ⋈ users(N:1)는 JOIN, order_items(1:N)는 IN 한 번. 페이지 크기와 무관하게 SQL 2(+COUNT 1)개 */
@Repository
class OrderQueryRepository {
	fun findById(id: Long): OrderResponse? = orderWithUser().where { Orders.id eq id }.toList().let(::assemble).singleOrNull()

	fun search(condition: OrderSearchCondition, pageable: Pageable): Page<OrderResponse> {
		val where = listOfNotNull<Op<Boolean>>(
			condition.userId?.let { Orders.userId eq it },
			condition.status?.let { Orders.status eq it },
		).reduceOrNull { acc, op -> acc and op } ?: Op.TRUE

		val rows = orderWithUser().where { where }
			.orderBy(*Orders.SORTABLE.toOrderBy(pageable.sort))
			.limit(pageable.pageSize).offset(pageable.offset)
			.toList() // Query는 지연 실행: 한 번만 실체화
		// COUNT에는 users JOIN이 필요 없다 (user_id NOT NULL FK)
		return PageableExecutionUtils.getPage(assemble(rows), pageable) { Orders.selectAll().where { where }.count() }
	}

	fun findByUserIdBefore(userId: Long, cursor: Long?, limit: Int): List<OrderResponse> =
		orderWithUser()
			.where { (Orders.userId eq userId) and (cursor?.let { Orders.id less it } ?: Op.TRUE) }
			.orderBy(Orders.id, SortOrder.DESC)
			.limit(limit)
			.toList().let(::assemble)

	private fun orderWithUser(): Query = (Orders innerJoin Users).select(Orders.columns + Users.name)

	private fun assemble(rows: List<ResultRow>): List<OrderResponse> {
		if (rows.isEmpty()) return emptyList()
		val lines = OrderItems.selectAll()
			.where { OrderItems.orderId inList rows.map { it[Orders.id].value } }
			.orderBy(OrderItems.id)
			.groupBy({ it[OrderItems.orderId].value }, { it.toLine() })
		return rows.map { row ->
			val id = row[Orders.id].value
			OrderResponse(id, row[Orders.userId].value, row[Users.name], row[Orders.status], row[Orders.totalAmount], row[Orders.orderedAt], lines[id].orEmpty())
		}
	}
}
