package com.example.shop.order

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

enum class OrderStatus { PLACED, CANCELED }

data class OrderPlaceRequest(
	@field:Positive
	val userId: Long,
	@field:Size(min = 1, max = 100)
	val lines: List<@Valid OrderLineRequest>,
)

data class OrderLineRequest(
	@field:Positive
	val itemId: Long,
	@field:Min(1) @field:Max(1_000)
	val quantity: Int,
)

data class OrderSearchCondition(
	val userId: Long? = null,
	val status: OrderStatus? = null,
)

data class OrderResponse(
	val orderId: Long,
	val userId: Long,
	val userName: String,
	val status: OrderStatus,
	val totalAmount: Long,
	val orderedAt: LocalDateTime,
	/** order_items.id 오름차순 */
	val lines: List<OrderLineResponse>,
)

data class OrderLineResponse(
	val itemId: Long,
	val itemName: String,
	val unitPrice: Long,
	val quantity: Int,
	val amount: Long,
)
