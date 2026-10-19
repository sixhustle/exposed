package com.example.shop.common.web;

import java.util.List;
import java.util.function.ToLongFunction;

/** 커서(keyset) 페이지. size + 1개를 조회해 다음 페이지 여부를 판단한다 */
public record CursorResponse<T>(List<T> content, Long nextCursor, boolean hasNext) {

    public static <T> CursorResponse<T> of(List<T> fetched, int size, ToLongFunction<T> cursorOf) {
        boolean hasNext = fetched.size() > size;
        List<T> content = fetched.subList(0, Math.min(size, fetched.size()));
        return new CursorResponse<>(content, hasNext ? cursorOf.applyAsLong(content.getLast()) : null, hasNext);
    }
}
