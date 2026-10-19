package com.example.shop.item;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** version: 클라이언트가 조회했던 버전. 그 사이 다른 변경이 있었다면 409 */
public record ItemUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @PositiveOrZero long price,
        @Min(0) long version) {
}
