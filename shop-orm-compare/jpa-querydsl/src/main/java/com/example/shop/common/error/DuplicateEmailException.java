package com.example.shop.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ShopException {

    public DuplicateEmailException(String email) {
        super(HttpStatus.CONFLICT, "이미 가입된 이메일입니다: " + email);
    }
}
