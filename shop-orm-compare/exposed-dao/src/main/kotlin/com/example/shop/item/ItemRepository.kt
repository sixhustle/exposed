package com.example.shop.item

import com.example.shop.common.exposed.flushChanges
import com.example.shop.common.exposed.startsWith
import com.example.shop.common.exposed.updateAudited
import com.example.shop.order.OrderItems
import com.example.shop.order.OrderStatus
import com.example.shop.order.Orders
import org.jetbrains.exposed.v1.core.CustomOperator
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ItemRepository {
	fun insert(name: String, price: Long, stock: Int): ItemEntity =
		ItemEntity.new {
			this.name = name
			this.price = price
			this.stock = stock
			this.version = 0
		}.also { flushChanges() }

	fun findById(id: Long): ItemEntity? = ItemEntity.findById(id)

	/** DAO의 flush UPDATE에는 WHERE version = ? 를 붙일 수 없다 → 행을 잠그고 비교한다 (사실상 비관적 락, 보고서 코드 E ①) */
	fun findByIdForUpdate(id: Long): ItemEntity? = ItemEntity.find { Items.id eq id }.forUpdate().singleOrNull()

	/** SELECT ... FOR UPDATE, id 순 (데드락 예방) */
	fun findAllByIdsForUpdate(ids: Collection<Long>): List<ItemEntity> =
		ItemEntity.find { Items.id inList ids }.orderBy(Items.id to SortOrder.ASC).forUpdate().toList()

	fun search(condition: ItemSearchCondition, pageable: Pageable): Page<ItemEntity> {
		// 동적 조건: null이 아닌 것만 AND. 원소 타입을 Op<Boolean>으로 명시해야 reduce가 컴파일된다
		val where = listOfNotNull<Op<Boolean>>(
			condition.keyword?.takeIf { it.isNotBlank() }?.let { Items.name like startsWith(it) },
			condition.minPrice?.let { Items.price greaterEq it },
			condition.maxPrice?.let { Items.price lessEq it },
		).reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
		val content = ItemEntity.find { where }
			.orderBy(*Items.SORTABLE.toOrderBy(pageable.sort))
			.limit(pageable.pageSize).offset(pageable.offset)
			.toList()
		return PageableExecutionUtils.getPage(content, pageable) { ItemEntity.count(where) }
	}

	/** 원자적 증가는 DAO setter(대입)로 쓸 수 없어 이 한 줄만 DSL. DSL UPDATE는 items의 DAO 캐시를 비운다 */
	fun increaseStock(id: Long, quantity: Int) {
		Items.updateAudited({ Items.id eq id }) {
			it[stock] = Items.stock + quantity
			it[version] = Items.version + 1
		}
	}

	/** 집계는 DAO에 없다 → DSL. 반개구간 [from, toExclusive), PLACED만 */
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

	fun flush() = flushChanges()
}
