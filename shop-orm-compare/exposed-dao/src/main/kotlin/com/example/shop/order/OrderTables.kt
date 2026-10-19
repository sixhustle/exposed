package com.example.shop.order

import com.example.shop.common.exposed.AuditableLongIdTable
import com.example.shop.common.exposed.SortableColumns
import com.example.shop.item.Items
import com.example.shop.user.Users
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.datetime

/** Flyway V1 스키마의 orders를 매핑한다 */
object Orders : AuditableLongIdTable("orders") {
	val userId = reference("user_id", Users, fkName = "fk_orders_user_id") // FK 컬럼일 뿐, 연관 객체가 아니다
	val status = enumerationByName("status", 20, OrderStatus::class)
	val totalAmount = long("total_amount")
	val orderedAt = datetime("ordered_at")

	/** 정렬 화이트리스트: id, orderedAt, totalAmount */
	val SORTABLE = SortableColumns(mapOf("id" to id, "orderedAt" to orderedAt, "totalAmount" to totalAmount), id)

	init {
		index("idx_orders_user_id_id", false, userId, id)
		index("idx_orders_status_ordered_at", false, status, orderedAt)
	}
}

object OrderItems : LongIdTable("order_items") {
	val orderId = reference("order_id", Orders, fkName = "fk_order_items_order_id")
	val itemId = reference("item_id", Items, fkName = "fk_order_items_item_id")
	val itemName = varchar("item_name", 100)
	val unitPrice = long("unit_price")
	val quantity = integer("quantity")
}
