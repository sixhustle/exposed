package com.example.shop.item

import com.example.shop.common.error.NotFoundException
import com.example.shop.common.error.OptimisticLockConflictException
import com.example.shop.common.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ItemService(private val itemRepository: ItemRepository) {

	@Transactional
	fun create(request: ItemCreateRequest): ItemResponse = itemRepository.insert(request.name, request.price, request.stock).toResponse()

	fun get(id: Long): ItemResponse = itemRepository.findById(id)?.toResponse() ?: throw NotFoundException("Item", id)

	/** 정렬 키: id, name, price, createdAt. 그 외는 400. 기본 id desc */
	fun search(condition: ItemSearchCondition, pageable: Pageable): PageResponse<ItemResponse> =
		PageResponse.of(itemRepository.search(condition, pageable).map { it.toResponse() })

	/** 낙관적 락. version이 다르면 409. 응답의 version은 수정 후 값 */
	@Transactional
	fun update(id: Long, request: ItemUpdateRequest): ItemResponse {
		val item = itemRepository.findByIdForUpdate(id) ?: throw NotFoundException("Item", id)
		if (item.version != request.version) throw OptimisticLockConflictException("Item", id)
		item.changeInfo(request.name, request.price) // setter: SQL 없음
		itemRepository.flush()                        // UPDATE items SET name=?, price=?, version=?, updated_at=?
		return item.toResponse()                      // 엔티티에 이미 새 version
	}

	/** [from 00:00, to+1일 00:00) 반개구간, PLACED만 */
	fun bestSellers(from: LocalDate, to: LocalDate, limit: Int): List<BestSellerResponse> =
		itemRepository.findBestSellers(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), limit)
}
