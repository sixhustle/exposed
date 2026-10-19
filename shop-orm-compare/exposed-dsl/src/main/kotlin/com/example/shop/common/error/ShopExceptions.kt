package com.example.shop.common.error

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException

/** 도메인 예외. ErrorResponseException이라 ProblemDetail(RFC 9457) 응답으로 바로 변환된다. */
abstract class ShopException(status: HttpStatus, detail: String) :
	ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null) {
	override val message: String = detail
}

class NotFoundException(resource: String, id: Any) :
	ShopException(HttpStatus.NOT_FOUND, "$resource(id=$id)를 찾을 수 없습니다")

class DuplicateEmailException(email: String) :
	ShopException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다: $email")

class InsufficientStockException(itemId: Long, requested: Int, stock: Int) :
	ShopException(HttpStatus.CONFLICT, "재고가 부족합니다 (itemId=$itemId, 요청=$requested, 재고=$stock)")

class OptimisticLockConflictException(resource: String, id: Any) :
	ShopException(HttpStatus.CONFLICT, "$resource(id=$id)가 다른 요청에 의해 먼저 수정되었습니다. 다시 조회 후 시도해 주세요")

class InvalidOrderStateException(detail: String) :
	ShopException(HttpStatus.CONFLICT, detail)

class InvalidSortPropertyException(property: String, allowed: Set<String>) :
	ShopException(HttpStatus.BAD_REQUEST, "정렬할 수 없는 속성입니다: $property (허용: ${allowed.joinToString()})")
