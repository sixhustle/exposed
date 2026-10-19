package com.example.shop.common.error;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ShopException {

    public InsufficientStockException(long itemId, int requested, int stock) {
        super(HttpStatus.CONFLICT, "재고가 부족합니다 (itemId=" + itemId + ", 요청=" + requested + ", 재고=" + stock + ")");
    }
}
