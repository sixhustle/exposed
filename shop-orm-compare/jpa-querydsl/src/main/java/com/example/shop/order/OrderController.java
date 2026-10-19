package com.example.shop.order;

import com.example.shop.common.web.CursorResponse;
import com.example.shop.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody OrderPlaceRequest request) {
        return orderService.place(request);
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable long orderId) {
        return orderService.cancel(orderId);
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderResponse get(@PathVariable long orderId) {
        return orderService.get(orderId);
    }

    // GET /api/orders?userId=1&status=PLACED&page=0&size=20&sort=orderedAt,desc
    @GetMapping("/api/orders")
    public PageResponse<OrderResponse> search(OrderSearchCondition condition, @PageableDefault(size = 20) Pageable pageable) {
        return orderService.search(condition, pageable);
    }

    // GET /api/users/1/orders?size=20 → 응답의 nextCursor를 다음 요청의 cursor로
    @GetMapping("/api/users/{userId}/orders")
    public CursorResponse<OrderResponse> listByUser(
            @PathVariable long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.listByUser(userId, cursor, size);
    }
}
