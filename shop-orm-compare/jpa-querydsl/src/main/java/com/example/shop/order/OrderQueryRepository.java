package com.example.shop.order;

import static com.example.shop.order.QOrder.order;
import static com.example.shop.user.QUser.user;

import com.example.shop.common.querydsl.SortablePaths;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * N:1(user)은 fetch join, 1:N(items)은 default_batch_fetch_size로 IN 한 번.
 * 컬렉션 fetch join + 페이징은 Hibernate가 메모리에서 페이징하므로 쓰지 않는다 (보고서 코드 B).
 */
@Repository
public class OrderQueryRepository {

    /** 정렬 화이트리스트: id, orderedAt, totalAmount */
    private static final SortablePaths SORTABLE =
            new SortablePaths(order.id, "id", order.id, "orderedAt", order.orderedAt, "totalAmount", order.totalAmount);

    private final JPAQueryFactory queryFactory;

    public OrderQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Optional<Order> findById(long id) {
        return Optional.ofNullable(queryFactory.selectFrom(order)
                .join(order.user, user).fetchJoin()
                .where(order.id.eq(id))
                .fetchOne());
    }

    public Page<Order> search(OrderSearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {userIdEq(condition.userId()), statusEq(condition.status())};
        List<Order> content = queryFactory.selectFrom(order)
                .join(order.user, user).fetchJoin()
                .where(where)
                .orderBy(SORTABLE.toOrderBy(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        // COUNT에는 users JOIN이 필요 없다
        JPAQuery<Long> count = queryFactory.select(order.count()).from(order).where(where);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    /** keyset: user_id = ? AND id < cursor ORDER BY id DESC */
    public List<Order> findByUserIdBefore(long userId, Long cursor, int limit) {
        return queryFactory.selectFrom(order)
                .join(order.user, user).fetchJoin()
                .where(userIdEq(userId), cursor != null ? order.id.lt(cursor) : null)
                .orderBy(order.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression userIdEq(Long userId) {
        return userId != null ? order.user.id.eq(userId) : null;
    }

    private BooleanExpression statusEq(OrderStatus status) {
        return status != null ? order.status.eq(status) : null;
    }
}
