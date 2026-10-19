package com.example.shop.order;

import com.example.shop.common.error.InvalidOrderStateException;
import com.example.shop.common.jpa.BaseTimeEntity;
import com.example.shop.item.Item;
import com.example.shop.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 스키마는 VARCHAR(20). @JdbcTypeCode로 VARCHAR 매핑을 명시한다 (ddl-auto=validate)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    @OrderBy("id")
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItem> items = new ArrayList<>();

    /** items는 id 순으로 잠긴 상품 목록, quantities는 상품별 합친 수량 */
    public static Order create(User user, List<Item> items, Map<Long, Integer> quantities, LocalDateTime orderedAt) {
        Order order = new Order();
        order.user = user;
        order.status = OrderStatus.PLACED;
        order.orderedAt = orderedAt;
        for (Item item : items) {
            int quantity = quantities.get(item.getId());
            order.items.add(new OrderItem(order, item, quantity)); // cascade PERSIST로 함께 저장
            order.totalAmount += item.getPrice() * quantity;
        }
        return order;
    }

    public void cancel() {
        if (status != OrderStatus.PLACED) {
            throw new InvalidOrderStateException("취소할 수 없는 주문 상태입니다 (orderId=" + id + ", status=" + status + ")");
        }
        this.status = OrderStatus.CANCELED;
    }
}
