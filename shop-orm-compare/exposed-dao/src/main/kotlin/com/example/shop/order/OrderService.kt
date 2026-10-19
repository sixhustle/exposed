package com.example.shop.order

import com.example.shop.common.ShopClock
import com.example.shop.common.error.InvalidOrderStateException
import com.example.shop.common.error.NotFoundException
import com.example.shop.common.web.CursorResponse
import com.example.shop.common.web.PageResponse
import com.example.shop.item.ItemRepository
import com.example.shop.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderService(
	private val userRepository: UserRepository,
	private val itemRepository: ItemRepository,
	private val orderRepository: OrderRepository,
) {

	/**
	 * 사용자 확인(404) → 같은 상품 줄 합치기 → 상품 행 id 순 FOR UPDATE → 재고 검증(409)·차감 → 주문·주문상품 저장.
	 * timeout은 트랜잭션 전체가 아니라 SQL 문장마다 5초 (보고서 08-2)
	 */
	@Transactional(timeout = 5)
	fun place(request: OrderPlaceRequest): OrderResponse {
		val user = userRepository.findById(request.userId) ?: throw NotFoundException("User", request.userId)
		val quantities = request.lines.groupBy({ it.itemId }, { it.quantity }).mapValues { (_, q) -> q.sum() }

		val items = itemRepository.findAllByIdsForUpdate(quantities.keys)
		(quantities.keys - items.map { it.id.value }.toSet()).firstOrNull()?.let { throw NotFoundException("Item", it) }

		items.forEach { it.decreaseStock(quantities.getValue(it.id.value)) } // 잠근 엔티티의 setter → flush 때 UPDATE
		return orderRepository.create(user, items, quantities, ShopClock.now()).toResponse()
	}

	/** 주문 행 FOR UPDATE → PLACED가 아니면 409 → CANCELED + 재고 원자적 복구 */
	@Transactional(timeout = 5)
	fun cancel(orderId: Long): OrderResponse {
		val order = orderRepository.findByIdForUpdate(orderId) ?: throw NotFoundException("Order", orderId)
		if (order.status != OrderStatus.PLACED) throw InvalidOrderStateException("취소할 수 없는 주문 상태입니다 (orderId=$orderId, status=${order.status})")
		order.status = OrderStatus.CANCELED
		order.items.sortedBy { it.itemId }.forEach { itemRepository.increaseStock(it.itemId, it.quantity) } // 원자적 증가는 DSL
		orderRepository.flush()
		return order.toResponse()
	}

	fun get(orderId: Long): OrderResponse = orderRepository.findById(orderId)?.toResponse() ?: throw NotFoundException("Order", orderId)

	/** 정렬 키: id, orderedAt, totalAmount */
	fun search(condition: OrderSearchCondition, pageable: Pageable): PageResponse<OrderResponse> =
		PageResponse.of(orderRepository.search(condition, pageable).map { it.toResponse() })

	/** keyset, size + 1개로 다음 페이지 판단 */
	fun listByUser(userId: Long, cursor: Long?, size: Int): CursorResponse<OrderResponse> =
		CursorResponse.of(orderRepository.findByUserIdBefore(userId, cursor, size + 1).map { it.toResponse() }, size) { it.orderId }
}
