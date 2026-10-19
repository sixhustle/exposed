package com.example.shop.item;

import com.example.shop.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@Valid @RequestBody ItemCreateRequest request) {
        return itemService.create(request);
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable long id) {
        return itemService.get(id);
    }

    // GET /api/items?keyword=펜&minPrice=1000&page=0&size=20&sort=price,asc
    @GetMapping
    public PageResponse<ItemResponse> search(ItemSearchCondition condition, @PageableDefault(size = 20) Pageable pageable) {
        return itemService.search(condition, pageable);
    }

    @PatchMapping("/{id}")
    public ItemResponse update(@PathVariable long id, @Valid @RequestBody ItemUpdateRequest request) {
        return itemService.update(id, request);
    }

    // GET /api/items/best-sellers?from=2026-10-01&to=2026-10-31&limit=10
    @GetMapping("/best-sellers")
    public List<BestSellerResponse> bestSellers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return itemService.bestSellers(from, to, limit);
    }
}
