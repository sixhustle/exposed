package com.example.shop.item;

/** version: 행이 바뀔 때마다 1 증가 (상품 정보 수정, 주문 재고 차감, 취소 재고 복구 모두) */
public record ItemResponse(long id, String name, long price, int stock, long version) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getPrice(), item.getStock(), item.getVersion());
    }
}
