package com.example.shop.order

import com.example.shop.common.web.CursorResponse
import com.example.shop.common.web.PageResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(private val orderService: OrderService) {

	@PostMapping("/api/orders")
	@ResponseStatus(HttpStatus.CREATED)
	fun place(@Valid @RequestBody request: OrderPlaceRequest): OrderResponse = orderService.place(request)

	@PostMapping("/api/orders/{orderId}/cancel")
	fun cancel(@PathVariable orderId: Long): OrderResponse = orderService.cancel(orderId)

	@GetMapping("/api/orders/{orderId}")
	fun get(@PathVariable orderId: Long): OrderResponse = orderService.get(orderId)

	// GET /api/orders?userId=1&status=PLACED&page=0&size=20&sort=orderedAt,desc
	@GetMapping("/api/orders")
	fun search(condition: OrderSearchCondition, @PageableDefault(size = 20) pageable: Pageable): PageResponse<OrderResponse> =
		orderService.search(condition, pageable)

	// GET /api/users/1/orders?size=20 → 응답의 nextCursor를 다음 요청의 cursor로
	@GetMapping("/api/users/{userId}/orders")
	fun listByUser(
		@PathVariable userId: Long,
		@RequestParam(required = false) cursor: Long?,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): CursorResponse<OrderResponse> = orderService.listByUser(userId, cursor, size)
}
