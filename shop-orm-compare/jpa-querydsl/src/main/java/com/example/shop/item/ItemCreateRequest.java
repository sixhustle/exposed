package com.example.shop.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ItemCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @PositiveOrZero long price,
        @PositiveOrZero int stock) {
}
