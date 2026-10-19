package com.example.shop.common.querydsl;

import com.example.shop.common.error.InvalidSortPropertyException;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * 정렬 화이트리스트: 클라이언트의 sort 파라미터를 허용한 Q타입 경로로만 바꾼다. 없는 키는 SQL을 만들기 전에 400.
 * 정렬이 없으면 id 내림차순. (Spring Data 파생 쿼리처럼 Sort를 그대로 넘기면 엔티티의 모든 필드가 정렬 대상이 된다)
 */
public final class SortablePaths {

    private final Map<String, ComparableExpressionBase<?>> paths;
    private final ComparableExpressionBase<?> id;

    /** keysAndPaths: "키", 경로, "키", 경로 ... (오류 메시지의 허용 키 순서를 유지하려고 순서 있는 맵으로 만든다) */
    public SortablePaths(ComparableExpressionBase<?> id, Object... keysAndPaths) {
        this.id = id;
        this.paths = new LinkedHashMap<>();
        for (int i = 0; i < keysAndPaths.length; i += 2) {
            paths.put((String) keysAndPaths[i], (ComparableExpressionBase<?>) keysAndPaths[i + 1]);
        }
    }

    public OrderSpecifier<?>[] toOrderBy(Sort sort) {
        if (sort.isUnsorted()) return new OrderSpecifier<?>[]{id.desc()};
        return sort.stream().map(order -> {
            ComparableExpressionBase<?> path = paths.get(order.getProperty());
            if (path == null) throw new InvalidSortPropertyException(order.getProperty(), paths.keySet());
            return order.isAscending() ? path.asc() : path.desc();
        }).toArray(OrderSpecifier<?>[]::new);
    }
}
