package com.example.shop.order;

import com.example.shop.common.ShopClock;
import com.example.shop.common.error.NotFoundException;
import com.example.shop.common.web.CursorResponse;
import com.example.shop.common.web.PageResponse;
import com.example.shop.item.Item;
import com.example.shop.item.ItemJpaRepository;
import com.example.shop.user.User;
import com.example.shop.user.UserJpaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final UserJpaRepository userRepository;
    private final ItemJpaRepository itemRepository;
    private final OrderJpaRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    public OrderService(UserJpaRepository userRepository, ItemJpaRepository itemRepository,
                        OrderJpaRepository orderRepository, OrderQueryRepository orderQueryRepository) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.orderQueryRepository = orderQueryRepository;
    }

    /**
     * 사용자 확인(404) → 같은 상품 줄 합치기 → 상품 행 id 순 FOR UPDATE → 재고 검증(409)·차감 → 주문·주문상품 저장.
     * JPA의 timeout은 트랜잭션 전체 마감(5초). 남은 시간이 각 쿼리에 나뉘어 들어간다 (Exposed는 문장당 5초, 보고서 08-2)
     */
    @Transactional(timeout = 5)
    public OrderResponse place(OrderPlaceRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User", request.userId()));
        Map<Long, Integer> quantities = request.lines().stream()
                .collect(Collectors.groupingBy(OrderLineRequest::itemId, Collectors.summingInt(OrderLineRequest::quantity)));

        List<Item> items = itemRepository.findAllByIdInOrderById(quantities.keySet()); // FOR UPDATE
        quantities.keySet().stream()
                .filter(id -> items.stream().noneMatch(item -> item.getId().equals(id)))
                .findFirst()
                .ifPresent(id -> { throw new NotFoundException("Item", id); });

        items.forEach(item -> item.decreaseStock(quantities.get(item.getId()))); // 더티 체킹 (+ @Version)
        Order order = orderRepository.save(Order.create(user, items, quantities, ShopClock.now()));
        orderRepository.flush(); // order_items INSERT와 items UPDATE를 여기서 보낸다 (응답의 id·version 확정)
        return OrderResponse.from(order);
    }

    /** 주문 행 FOR UPDATE → PLACED가 아니면 409 → CANCELED + 재고 원자적 복구 */
    @Transactional(timeout = 5)
    public OrderResponse cancel(long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        order.cancel();
        List<OrderItem> lines = order.getItems().stream().sorted(Comparator.comparing(OrderItem::getItemId)).toList();
        // 첫 increaseStock 직전에 주문 상태가 flush되고(flushAutomatically), 직후 영속성 컨텍스트가 비워진다(clearAutomatically)
        lines.forEach(line -> itemRepository.increaseStock(line.getItemId(), line.getQuantity(), ShopClock.now()));
        return OrderResponse.from(orderQueryRepository.findById(orderId).orElseThrow()); // 비워졌으니 다시 읽는다
    }

    public OrderResponse get(long orderId) {
        return orderQueryRepository.findById(orderId).map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    /** 정렬 키: id, orderedAt, totalAmount */
    public PageResponse<OrderResponse> search(OrderSearchCondition condition, Pageable pageable) {
        return PageResponse.of(orderQueryRepository.search(condition, pageable).map(OrderResponse::from));
    }

    /** keyset, size + 1개로 다음 페이지 판단 */
    public CursorResponse<OrderResponse> listByUser(long userId, Long cursor, int size) {
        List<OrderResponse> fetched = orderQueryRepository.findByUserIdBefore(userId, cursor, size + 1).stream()
                .map(OrderResponse::from).toList();
        return CursorResponse.of(fetched, size, OrderResponse::orderId);
    }
}
