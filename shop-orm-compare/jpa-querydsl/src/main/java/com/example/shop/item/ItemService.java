package com.example.shop.item;

import com.example.shop.common.error.NotFoundException;
import com.example.shop.common.error.OptimisticLockConflictException;
import com.example.shop.common.web.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemJpaRepository itemRepository;
    private final ItemQueryRepository itemQueryRepository;

    public ItemService(ItemJpaRepository itemRepository, ItemQueryRepository itemQueryRepository) {
        this.itemRepository = itemRepository;
        this.itemQueryRepository = itemQueryRepository;
    }

    @Transactional
    public ItemResponse create(ItemCreateRequest request) {
        return ItemResponse.from(itemRepository.save(new Item(request.name(), request.price(), request.stock())));
    }

    public ItemResponse get(long id) {
        return itemRepository.findById(id).map(ItemResponse::from).orElseThrow(() -> new NotFoundException("Item", id));
    }

    /** 정렬 키: id, name, price, createdAt. 그 외는 400. 기본 id desc */
    public PageResponse<ItemResponse> search(ItemSearchCondition condition, Pageable pageable) {
        return PageResponse.of(itemQueryRepository.search(condition, pageable).map(ItemResponse::from));
    }

    /**
     * 낙관적 락. 클라이언트가 본 version과 비교 → 다르면 409. 그 사이 동시 수정은 flush의 WHERE version = ? 가 잡는다
     * (ObjectOptimisticLockingFailureException → GlobalExceptionHandler 409).
     * flush()를 응답 전에 부르지 않으면 응답의 version이 증가 전 값이 된다 (보고서 코드 E 측정)
     */
    @Transactional
    public ItemResponse update(long id, ItemUpdateRequest request) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new NotFoundException("Item", id));
        if (item.getVersion() != request.version()) throw new OptimisticLockConflictException("Item", id);
        item.changeInfo(request.name(), request.price());
        itemRepository.flush();
        return ItemResponse.from(item);
    }

    /** [from 00:00, to+1일 00:00) 반개구간, PLACED만 */
    public List<BestSellerResponse> bestSellers(LocalDate from, LocalDate to, int limit) {
        return itemQueryRepository.findBestSellers(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), limit);
    }
}
