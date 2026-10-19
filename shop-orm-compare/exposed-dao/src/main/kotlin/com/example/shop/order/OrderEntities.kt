package com.example.shop.order

import com.example.shop.common.exposed.AuditedLongEntity
import com.example.shop.item.ItemEntity
import com.example.shop.user.UserEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class OrderEntity(id: EntityID<Long>) : AuditedLongEntity(id, Orders) {
	companion object : LongEntityClass<OrderEntity>(Orders)

	var user by UserEntity referencedOn Orders.userId              // N:1 지연 로딩
	var status by Orders.status
	var totalAmount by Orders.totalAmount
	var orderedAt by Orders.orderedAt
	val items by OrderItemEntity referrersOn OrderItems.orderId     // 1:N 지연 로딩. 순서가 보장되지 않아 응답에서 id로 정렬

	fun toResponse() = OrderResponse(
		id.value, user.id.value, user.name, status, totalAmount, orderedAt,
		items.sortedBy { it.id.value }.map { it.toLine() },
	)
}

class OrderItemEntity(id: EntityID<Long>) : LongEntity(id) {
	companion object : LongEntityClass<OrderItemEntity>(OrderItems)

	var order by OrderEntity referencedOn OrderItems.orderId
	var item by ItemEntity referencedOn OrderItems.itemId
	var itemName by OrderItems.itemName
	var unitPrice by OrderItems.unitPrice
	var quantity by OrderItems.quantity

	/** item(참조 엔티티)을 거치면 ItemEntity SELECT가 나간다. FK 값만 필요하면 읽어 둔 컬럼 값을 쓴다 */
	val itemId: Long get() = readValues[OrderItems.itemId].value

	fun toLine() = OrderLineResponse(itemId, itemName, unitPrice, quantity, unitPrice * quantity)
}
