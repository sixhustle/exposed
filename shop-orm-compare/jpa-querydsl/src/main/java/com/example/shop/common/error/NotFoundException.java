package com.example.shop.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ShopException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, resource + "(id=" + id + ")를 찾을 수 없습니다");
    }
}
