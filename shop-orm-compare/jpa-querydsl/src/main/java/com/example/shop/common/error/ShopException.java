package com.example.shop.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 도메인 예외. ErrorResponseException이라 ProblemDetail(RFC 9457) 응답으로 바로 변환된다 */
public abstract class ShopException extends ErrorResponseException {

    private final String detail;

    protected ShopException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        return detail;
    }
}
