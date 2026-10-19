package com.example.shop.order;

import java.time.LocalDateTime;
import java.util.List;

/** lines는 order_items.id 오름차순 */
public record OrderResponse(
        long orderId, long userId, String userName, OrderStatus status, long totalAmount, LocalDateTime orderedAt,
        List<OrderLineResponse> lines) {

    /** 트랜잭션 안에서 호출한다 (user·items 지연 로딩). items는 @OrderBy("id") + default_batch_fetch_size로 IN 로딩 */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getUser().getId(), order.getUser().getName(), order.getStatus(),
                order.getTotalAmount(), order.getOrderedAt(),
                order.getItems().stream().map(OrderLineResponse::from).toList());
    }
}
