package com.example.shop.common.error;

import org.springframework.http.HttpStatus;

public class InvalidSortPropertyException extends ShopException {

    public InvalidSortPropertyException(String property, java.util.Collection<String> allowed) {
        super(HttpStatus.BAD_REQUEST, "정렬할 수 없는 속성입니다: " + property + " (허용: " + String.join(", ", allowed) + ")");
    }
}
