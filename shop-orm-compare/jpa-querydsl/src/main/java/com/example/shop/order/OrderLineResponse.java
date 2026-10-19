package com.example.shop.order;

public record OrderLineResponse(long itemId, String itemName, long unitPrice, int quantity, long amount) {

    public static OrderLineResponse from(OrderItem line) {
        return new OrderLineResponse(line.getItemId(), line.getItemName(), line.getUnitPrice(), line.getQuantity(),
                line.getUnitPrice() * line.getQuantity());
    }
}
