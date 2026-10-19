package com.example.shop.item;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ItemJpaRepository extends JpaRepository<Item, Long> {

    /** SELECT ... FOR UPDATE ORDER BY id (데드락 예방) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Item> findAllByIdInOrderById(Collection<Long> ids);

    /**
     * 원자적 증가 (stock = stock + ?). JPQL 벌크 UPDATE는 영속성 컨텍스트·@Version·Auditing을 모두 우회한다.
     * - version, updatedAt을 직접 쓴다 (리스너가 돌지 않음)
     * - flushAutomatically: 이 UPDATE 전에 같은 트랜잭션의 변경(주문 상태)을 먼저 flush
     * - clearAutomatically: 끝나고 영속성 컨텍스트를 비워, 이미 로딩한 Item이 옛 값을 보지 않게
     *   (flushAutomatically 없이 clear만 하면 flush 안 된 주문 상태 변경이 버려진다)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Item i set i.stock = i.stock + :quantity, i.version = i.version + 1, i.updatedAt = :now where i.id = :id")
    int increaseStock(Long id, int quantity, LocalDateTime now);
}
