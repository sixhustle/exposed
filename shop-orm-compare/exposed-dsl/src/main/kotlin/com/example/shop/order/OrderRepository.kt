package com.example.shop.order

import com.example.shop.common.exposed.updateAudited
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

fun ResultRow.toLine() = OrderLineResponse(
	this[OrderItems.itemId].value, this[OrderItems.itemName], this[OrderItems.unitPrice], this[OrderItems.quantity],
	this[OrderItems.unitPrice] * this[OrderItems.quantity],
)

data class NewOrderLine(val itemId: Long, val itemName: String, val unitPrice: Long, val quantity: Int)

/** 주문 쓰기. 조회 화면용은 OrderQueryRepository */
@Repository
class OrderRepository {
	/** cascade PERSIST 대신 두 문장: orders INSERT → 생성 id로 order_items batchInsert */
	fun insert(userId: Long, lines: List<NewOrderLine>, orderedAt: LocalDateTime): Long {
		val orderId = Orders.insertAndGetId {
			it[Orders.userId] = userId
			it[status] = OrderStatus.PLACED
			it[totalAmount] = lines.sumOf { line -> line.unitPrice * line.quantity }
			it[Orders.orderedAt] = orderedAt
		}
		OrderItems.batchInsert(lines, shouldReturnGeneratedValues = false) { line ->
			this[OrderItems.orderId] = orderId
			this[OrderItems.itemId] = line.itemId
			this[OrderItems.itemName] = line.itemName
			this[OrderItems.unitPrice] = line.unitPrice
			this[OrderItems.quantity] = line.quantity
		}
		return orderId.value
	}

	/** 동시 취소가 둘 다 PLACED를 보고 재고를 두 번 복구하지 않게 주문 행을 잠근다 */
	fun findStatusForUpdate(id: Long): OrderStatus? =
		Orders.select(Orders.status).where { Orders.id eq id }
			.forUpdate(ForUpdateOption.ForUpdate)
			.singleOrNull()?.get(Orders.status)

	fun updateStatus(id: Long, status: OrderStatus) {
		Orders.updateAudited({ Orders.id eq id }) { it[Orders.status] = status }
	}

	/** 재고 복구 순서를 itemId로 고정 (데드락 예방) */
	fun findLines(orderId: Long): List<OrderLineResponse> =
		OrderItems.selectAll().where { OrderItems.orderId eq orderId }.orderBy(OrderItems.itemId).map { it.toLine() }
}
