package com.example.shop.item

import com.example.shop.common.web.PageResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/items")
class ItemController(private val itemService: ItemService) {

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@Valid @RequestBody request: ItemCreateRequest): ItemResponse = itemService.create(request)

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): ItemResponse = itemService.get(id)

	// GET /api/items?keyword=펜&minPrice=1000&page=0&size=20&sort=price,asc
	@GetMapping
	fun search(condition: ItemSearchCondition, @PageableDefault(size = 20) pageable: Pageable): PageResponse<ItemResponse> =
		itemService.search(condition, pageable)

	@PatchMapping("/{id}")
	fun update(@PathVariable id: Long, @Valid @RequestBody request: ItemUpdateRequest): ItemResponse =
		itemService.update(id, request)

	// GET /api/items/best-sellers?from=2026-10-01&to=2026-10-31&limit=10
	@GetMapping("/best-sellers")
	fun bestSellers(
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) limit: Int,
	): List<BestSellerResponse> = itemService.bestSellers(from, to, limit)
}
