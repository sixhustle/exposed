package com.example.shop.common.error;

import org.springframework.http.HttpStatus;

public class InvalidOrderStateException extends ShopException {

    public InvalidOrderStateException(String detail) {
        super(HttpStatus.CONFLICT, detail);
    }
}
