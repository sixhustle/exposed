package com.example.shop.common.error;

import org.springframework.http.HttpStatus;

public class OptimisticLockConflictException extends ShopException {

    public OptimisticLockConflictException(String resource, Object id) {
        super(HttpStatus.CONFLICT, resource + "(id=" + id + ")가 다른 요청에 의해 먼저 수정되었습니다. 다시 조회 후 시도해 주세요");
    }
}
