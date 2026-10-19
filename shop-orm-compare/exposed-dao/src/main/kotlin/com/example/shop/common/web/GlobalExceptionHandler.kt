package com.example.shop.common.web

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 데이터 접근 예외 → HTTP. Exposed는 common.exposed의 번역 인터셉터가 DataAccessException으로 바꿔 둔다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

	@ExceptionHandler(DataIntegrityViolationException::class)
	fun handleIntegrityViolation(e: DataIntegrityViolationException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "데이터 제약 조건을 위반했습니다")

	@ExceptionHandler(PessimisticLockingFailureException::class, QueryTimeoutException::class)
	fun handleLockFailure(e: RuntimeException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요")
}
