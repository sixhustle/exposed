package com.example.library.common.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 예외를 RFC 9457 ProblemDetail 형식의 JSON 응답으로 바꾼다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(NotFoundException::class)
	fun handleNotFound(e: NotFoundException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "리소스를 찾을 수 없습니다")

	@ExceptionHandler(BusinessException::class)
	fun handleBusiness(e: BusinessException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "요청을 처리할 수 없습니다")

	// @Valid 검증 실패 (예: 빈 제목, 음수 가격)
	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(e: MethodArgumentNotValidException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다").apply {
			setProperty("errors", e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") })
		}

	// Kotlin의 non-null 필드가 JSON에 없거나 null이면 검증 전에 역직렬화 단계에서 실패한다
	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleNotReadable(e: HttpMessageNotReadableException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다 (필수 값 누락 또는 타입 불일치)")
}
