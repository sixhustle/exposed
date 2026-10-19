package com.example.shop.item;

import com.example.shop.common.error.InsufficientStockException;
import com.example.shop.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL: IDENTITY라 Hibernate가 INSERT JDBC 배치를 끈다 (보고서 01-15)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    /** flush UPDATE에 WHERE version = ? 와 +1이 자동으로 붙는다. 엔티티가 바뀌면 항상 +1 (재고 차감 포함) */
    @Version
    private long version;

    public Item(String name, long price, int stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public void decreaseStock(int quantity) {
        if (stock < quantity) throw new InsufficientStockException(id, quantity, stock);
        this.stock -= quantity; // 더티 체킹: flush 때 UPDATE
    }

    public void changeInfo(String name, long price) {
        this.name = name;
        this.price = price;
    }
}
