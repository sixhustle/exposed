package com.example.shop.common.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 데이터 접근 예외 → HTTP. JPA 예외는 Spring이 DataAccessException으로 번역해 둔다 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "데이터 제약 조건을 위반했습니다");
    }

    /** @Version 충돌: flush 시점의 ObjectOptimisticLockingFailureException (Exposed 프로젝트에는 없는 경로) */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "다른 요청에 의해 먼저 수정되었습니다. 다시 조회 후 시도해 주세요");
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    public ProblemDetail handleLockFailure(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요");
    }
}
