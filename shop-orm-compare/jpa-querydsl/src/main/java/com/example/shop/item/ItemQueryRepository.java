package com.example.shop.item;

import static com.example.shop.item.QItem.item;
import static com.example.shop.order.QOrder.order;
import static com.example.shop.order.QOrderItem.orderItem;

import com.example.shop.common.querydsl.SortablePaths;
import com.example.shop.order.OrderStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ItemQueryRepository {

    /** 정렬 화이트리스트: id, name, price, createdAt */
    private static final SortablePaths SORTABLE =
            new SortablePaths(item.id, "id", item.id, "name", item.name, "price", item.price, "createdAt", item.createdAt);

    private final JPAQueryFactory queryFactory;

    public ItemQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Page<Item> search(ItemSearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {
                nameStartsWith(condition.keyword()), priceGoe(condition.minPrice()), priceLoe(condition.maxPrice())};

        List<Item> content = queryFactory.selectFrom(item)
                .where(where)                                     // null 조건은 빠진다
                .orderBy(SORTABLE.toOrderBy(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(item.count()).from(item).where(where);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    /** 3-엔티티 JOIN + GROUP BY. 반개구간 [from, toExclusive), PLACED만 */
    public List<BestSellerResponse> findBestSellers(LocalDateTime from, LocalDateTime toExclusive, int limit) {
        // QueryDSL 7.x: sum()은 sumAggregate()(원래 타입)·sumLong() 등으로 바뀌었다 (5.x 예제와 다름)
        NumberExpression<Long> soldQuantity = orderItem.quantity.sumLong();
        NumberExpression<Long> salesAmount = orderItem.unitPrice.multiply(orderItem.quantity).sumLong();
        List<Tuple> rows = queryFactory.select(item.id, item.name, soldQuantity, salesAmount)
                .from(orderItem)
                .join(orderItem.order, order)
                .join(orderItem.item, item)
                .where(order.status.eq(OrderStatus.PLACED), order.orderedAt.goe(from), order.orderedAt.lt(toExclusive))
                .groupBy(item.id, item.name)
                .orderBy(soldQuantity.desc(), item.id.asc())
                .limit(limit)
                .fetch();
        return rows.stream()
                .map(t -> new BestSellerResponse(t.get(item.id), t.get(item.name), t.get(soldQuantity), t.get(salesAmount)))
                .toList();
    }

    private BooleanExpression nameStartsWith(String keyword) {
        // startsWith는 입력의 %, _ 를 이스케이프한다 (like ? escape '!')
        return StringUtils.hasText(keyword) ? item.name.startsWith(keyword) : null;
    }

    private BooleanExpression priceGoe(Long minPrice) {
        return minPrice != null ? item.price.goe(minPrice) : null;
    }

    private BooleanExpression priceLoe(Long maxPrice) {
        return maxPrice != null ? item.price.loe(maxPrice) : null;
    }
}
