package com.example.shop.item

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class ItemCreateRequest(
	@field:NotBlank @field:Size(max = 100)
	val name: String,
	@field:PositiveOrZero
	val price: Long,
	@field:PositiveOrZero
	val stock: Int,
)

data class ItemUpdateRequest(
	@field:NotBlank @field:Size(max = 100)
	val name: String,
	@field:PositiveOrZero
	val price: Long,
	/** 클라이언트가 조회했던 버전. 그 사이 다른 변경이 있었다면 409 */
	@field:Min(0)
	val version: Long,
)

/** 동적 조건: 값이 없는 조건은 WHERE에서 빠진다 */
data class ItemSearchCondition(
	val keyword: String? = null,  // 이름 prefix (LIKE 'keyword%', %·_ 이스케이프)
	val minPrice: Long? = null,
	val maxPrice: Long? = null,
)

data class ItemResponse(
	val id: Long,
	val name: String,
	val price: Long,
	val stock: Int,
	/** 행이 바뀔 때마다 1 증가 (상품 정보 수정, 주문 재고 차감, 취소 재고 복구 모두). JPA @Version의 기본 의미에 세 프로젝트를 맞췄다 */
	val version: Long,
)

data class BestSellerResponse(
	val itemId: Long,
	val itemName: String,
	val soldQuantity: Long,
	val salesAmount: Long,
)
