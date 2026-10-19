package com.example.shop.order

import com.example.shop.common.ShopClock
import com.example.shop.common.error.InsufficientStockException
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
	private val orderQueryRepository: OrderQueryRepository,
) {

	/**
	 * 사용자 확인(404) → 같은 상품 줄 합치기 → 상품 행 id 순 FOR UPDATE → 재고 검증(409)·차감 → 주문·주문상품 저장.
	 * timeout은 트랜잭션 전체가 아니라 SQL 문장마다 5초 (보고서 08-2)
	 */
	@Transactional(timeout = 5)
	fun place(request: OrderPlaceRequest): OrderResponse {
		if (!userRepository.existsById(request.userId)) throw NotFoundException("User", request.userId)
		val quantities = request.lines.groupBy({ it.itemId }, { it.quantity }).mapValues { (_, q) -> q.sum() }

		val items = itemRepository.findAllByIdsForUpdate(quantities.keys)
		(quantities.keys - items.map { it.id }.toSet()).firstOrNull()?.let { throw NotFoundException("Item", it) }

		items.forEach { item ->
			val quantity = quantities.getValue(item.id)
			if (item.stock < quantity) throw InsufficientStockException(item.id, quantity, item.stock)
			itemRepository.decreaseStock(item.id, quantity)
		}
		val orderId = orderRepository.insert(
			request.userId,
			items.map { NewOrderLine(it.id, it.name, it.price, quantities.getValue(it.id)) },
			ShopClock.now(),
		)
		return orderQueryRepository.findById(orderId)!! // 같은 writer 트랜잭션에서 재조회
	}

	/** 주문 행 FOR UPDATE → PLACED가 아니면 409 → CANCELED + 재고 원자적 복구 */
	@Transactional(timeout = 5)
	fun cancel(orderId: Long): OrderResponse {
		val status = orderRepository.findStatusForUpdate(orderId) ?: throw NotFoundException("Order", orderId)
		if (status != OrderStatus.PLACED) throw InvalidOrderStateException("취소할 수 없는 주문 상태입니다 (orderId=$orderId, status=$status)")
		orderRepository.updateStatus(orderId, OrderStatus.CANCELED)
		orderRepository.findLines(orderId).forEach { itemRepository.increaseStock(it.itemId, it.quantity) }
		return orderQueryRepository.findById(orderId)!!
	}

	fun get(orderId: Long): OrderResponse = orderQueryRepository.findById(orderId) ?: throw NotFoundException("Order", orderId)

	/** 정렬 키: id, orderedAt, totalAmount */
	fun search(condition: OrderSearchCondition, pageable: Pageable): PageResponse<OrderResponse> =
		PageResponse.of(orderQueryRepository.search(condition, pageable))

	/** keyset: user_id = ? AND id < cursor ORDER BY id DESC, size + 1개로 다음 페이지 판단 */
	fun listByUser(userId: Long, cursor: Long?, size: Int): CursorResponse<OrderResponse> =
		CursorResponse.of(orderQueryRepository.findByUserIdBefore(userId, cursor, size + 1), size) { it.orderId }
}
