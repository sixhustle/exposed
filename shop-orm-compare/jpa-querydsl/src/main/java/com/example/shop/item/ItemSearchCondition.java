package com.example.shop.item;

/** 동적 조건: 값이 없는 조건은 WHERE에서 빠진다. keyword는 이름 prefix (%·_ 이스케이프) */
public record ItemSearchCondition(String keyword, Long minPrice, Long maxPrice) {
}
