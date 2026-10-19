package com.example.shop.item

import com.example.shop.common.exposed.startsWith
import com.example.shop.common.exposed.updateAudited
import com.example.shop.order.OrderItems
import com.example.shop.order.OrderStatus
import com.example.shop.order.Orders
import org.jetbrains.exposed.v1.core.CustomOperator
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

fun ResultRow.toItemResponse() = ItemResponse(this[Items.id].value, this[Items.name], this[Items.price], this[Items.stock], this[Items.version])

@Repository
class ItemRepository {
	fun insert(name: String, price: Long, stock: Int): ItemResponse =
		Items.insert {
			it[Items.name] = name
			it[Items.price] = price
			it[Items.stock] = stock
			it[version] = 0
		}.resultedValues!!.single().toItemResponse()

	fun findById(id: Long): ItemResponse? = Items.selectAll().where { Items.id eq id }.singleOrNull()?.toItemResponse()

	fun search(condition: ItemSearchCondition, pageable: Pageable): Page<ItemResponse> {
		val where = condition.toOp()
		val content = Items.selectAll().where { where }
			.orderBy(*Items.SORTABLE.toOrderBy(pageable.sort))
			.limit(pageable.pageSize).offset(pageable.offset)
			.map { it.toItemResponse() }
		return PageableExecutionUtils.getPage(content, pageable) { Items.selectAll().where { where }.count() }
	}

	/** 동적 조건: null이 아닌 것만 AND. 원소 타입을 Op<Boolean>으로 명시해야 reduce가 컴파일된다 */
	private fun ItemSearchCondition.toOp(): Op<Boolean> =
		listOfNotNull<Op<Boolean>>(
			keyword?.takeIf { it.isNotBlank() }?.let { Items.name like startsWith(it) },
			minPrice?.let { Items.price greaterEq it },
			maxPrice?.let { Items.price lessEq it },
		).reduceOrNull { acc, op -> acc and op } ?: Op.TRUE

	/** WHERE version = ? 조건 UPDATE + 반영 행 수로 충돌 판단 (JPA @Version이 해 주던 일) */
	fun updateIfVersionMatches(id: Long, name: String, price: Long, expectedVersion: Long): Boolean =
		Items.updateAudited({ (Items.id eq id) and (Items.version eq expectedVersion) }) {
			it[Items.name] = name
			it[Items.price] = price
			it[version] = Items.version + 1
		} == 1

	/** SELECT ... FOR UPDATE, id 순 (데드락 예방) */
	fun findAllByIdsForUpdate(ids: Collection<Long>): List<ItemResponse> =
		Items.selectAll().where { Items.id inList ids }
			.orderBy(Items.id)
			.forUpdate(ForUpdateOption.ForUpdate)
			.map { it.toItemResponse() }

	/** 잠근 뒤 호출한다. 재고 변경도 행 변경이므로 version + 1 */
	fun decreaseStock(id: Long, quantity: Int) {
		Items.updateAudited({ Items.id eq id }) {
			it[stock] = Items.stock - quantity
			it[version] = Items.version + 1
		}
	}

	/** 원자적 증가: 락 없이도 갱신 유실이 없다 */
	fun increaseStock(id: Long, quantity: Int) {
		Items.updateAudited({ Items.id eq id }) {
			it[stock] = Items.stock + quantity
			it[version] = Items.version + 1
		}
	}

	/** 3-테이블 JOIN + GROUP BY. 반개구간 [from, toExclusive), PLACED만 (인덱스 (status, ordered_at)) */
	fun findBestSellers(from: LocalDateTime, toExclusive: LocalDateTime, limit: Int): List<BestSellerResponse> {
		val soldQuantity = OrderItems.quantity.sum()
		val salesAmount = CustomOperator("*", LongColumnType(), OrderItems.unitPrice, OrderItems.quantity).sum()
		return OrderItems
			.join(Orders, JoinType.INNER, OrderItems.orderId, Orders.id)
			.join(Items, JoinType.INNER, OrderItems.itemId, Items.id)
			.select(Items.id, Items.name, soldQuantity, salesAmount)
			.where { (Orders.status eq OrderStatus.PLACED) and (Orders.orderedAt greaterEq from) and (Orders.orderedAt less toExclusive) }
			.groupBy(Items.id, Items.name)
			.orderBy(soldQuantity to SortOrder.DESC, Items.id to SortOrder.ASC)
			.limit(limit)
			.map { BestSellerResponse(it[Items.id].value, it[Items.name], it[soldQuantity]?.toLong() ?: 0, it[salesAmount] ?: 0) }
	}
}
