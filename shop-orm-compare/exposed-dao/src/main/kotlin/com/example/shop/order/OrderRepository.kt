package com.example.shop.order

import com.example.shop.common.exposed.flushChanges
import com.example.shop.item.ItemEntity
import com.example.shop.user.UserEntity
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.dao.with
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderRepository {
	/** cascade 선언이 없다: 부모와 자식을 각각 new {}. flush 때 orders INSERT 1 + order_items 배치 INSERT 1 */
	fun create(user: UserEntity, items: List<ItemEntity>, quantities: Map<Long, Int>, orderedAt: LocalDateTime): OrderEntity {
		val order = OrderEntity.new {
			this.user = user
			this.status = OrderStatus.PLACED
			this.totalAmount = items.sumOf { it.price * quantities.getValue(it.id.value) }
			this.orderedAt = orderedAt
		}
		items.forEach { item ->
			OrderItemEntity.new {
				this.order = order
				this.item = item
				this.itemName = item.name
				this.unitPrice = item.price
				this.quantity = quantities.getValue(item.id.value)
			}
		}
		flushChanges()
		return order
	}

	/** 동시 취소가 둘 다 PLACED를 보고 재고를 두 번 복구하지 않게 주문 행을 잠근다 */
	fun findByIdForUpdate(id: Long): OrderEntity? = OrderEntity.find { Orders.id eq id }.forUpdate().singleOrNull()

	/** 단건: user·items를 선로딩 */
	fun findById(id: Long): OrderEntity? = OrderEntity.findById(id)?.load(OrderEntity::user, OrderEntity::items)

	/** with()를 빠뜨리면 주문마다 user·items SELECT (N+1). with()면 페이지 크기와 무관하게 IN 2개 */
	fun search(condition: OrderSearchCondition, pageable: Pageable): Page<OrderEntity> {
		val where = listOfNotNull<Op<Boolean>>(
			condition.userId?.let { Orders.userId eq it },
			condition.status?.let { Orders.status eq it },
		).reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
		val content = OrderEntity.find { where }
			.orderBy(*Orders.SORTABLE.toOrderBy(pageable.sort))
			.limit(pageable.pageSize).offset(pageable.offset)
			.with(OrderEntity::user, OrderEntity::items)
			.toList()
		return PageableExecutionUtils.getPage(content, pageable) { OrderEntity.count(where) }
	}

	/** keyset: user_id = ? AND id < cursor ORDER BY id DESC */
	fun findByUserIdBefore(userId: Long, cursor: Long?, limit: Int): List<OrderEntity> =
		OrderEntity.find { (Orders.userId eq userId) and (cursor?.let { Orders.id less it } ?: Op.TRUE) }
			.orderBy(Orders.id to SortOrder.DESC)
			.limit(limit)
			.with(OrderEntity::user, OrderEntity::items)
			.toList()

	fun flush() = flushChanges()
}
